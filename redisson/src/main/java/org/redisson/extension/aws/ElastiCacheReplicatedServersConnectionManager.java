/**
 * Copyright 2016 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.extension.aws;

import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RFuture;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.config.BaseMasterSlaveServersConfig;
import org.redisson.config.Config;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.connection.MasterSlaveConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.misc.URLBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.elasticache.AmazonElastiCache;
import com.amazonaws.services.elasticache.model.DescribeReplicationGroupsRequest;
import com.amazonaws.services.elasticache.model.DescribeReplicationGroupsResult;
import com.amazonaws.services.elasticache.model.Endpoint;
import com.amazonaws.services.elasticache.model.NodeGroup;
import com.amazonaws.services.elasticache.model.NodeGroupMember;

import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.ScheduledFuture;

public class ElastiCacheReplicatedServersConnectionManager extends MasterSlaveConnectionManager {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private Endpoint currentMaster;

	private Set<Endpoint> slaves = new HashSet<>();

	private Set<Endpoint> disconnectedSlaves = new HashSet<>();

	private final Map<URL, RedisConnection> nodeConnections = new HashMap<URL, RedisConnection>();

	private ScheduledFuture<?> monitorFuture;

	public ElastiCacheReplicatedServersConnectionManager(ElasticCacheReplicationGroupServersConfig cfg, Config config) {
		super(config);
		this.config = create(cfg);
		initTimer(this.config);
		AmazonElastiCache elastiCacheClient = cfg.getAwsElastiCacheClient();
		DescribeReplicationGroupsRequest dccRequest = new DescribeReplicationGroupsRequest();
		dccRequest.setReplicationGroupId(cfg.getReplicationGroupId());
		DescribeReplicationGroupsResult clusterResult = null;
		try {
			clusterResult = elastiCacheClient.describeReplicationGroups(dccRequest);
		} catch (Throwable e) {
			log.error("Error getting nodes from Aws elasticache, we will not retry ", e);
			throw new RedisConnectionException("Can't connect to servers!");
		}
		log.info("{} is a clusterResult", clusterResult);
		for (NodeGroup group : clusterResult.getReplicationGroups().get(0).getNodeGroups()) {
			log.info("{} is a group", group);
			for (NodeGroupMember member : group.getNodeGroupMembers()) {
				log.info("{} is a member", member);
				URL node = URLBuilder
						.create(member.getReadEndpoint().getAddress() + ":" + member.getReadEndpoint().getPort());
				if (member.getCurrentRole().equals("primary")) {
					currentMaster = member.getReadEndpoint();
					this.config.setMasterAddress(
							URLBuilder.create(currentMaster.getAddress() + ":" + currentMaster.getPort()));
					log.info("{} is a master", node);
				} else {
					log.info("{} is a slave", node);
					slaves.add(member.getReadEndpoint());
					this.config.addSlaveAddress(node);
				}

			}
		}
		if (currentMaster == null) {
			throw new RedisConnectionException("Can't connect to servers!");
		}
		init(this.config);
		scheduleReplicationGroupChangeCheck(cfg);
	}

	@Override
	protected MasterSlaveServersConfig create(BaseMasterSlaveServersConfig<?> cfg) {
		MasterSlaveServersConfig res = super.create(cfg);
		res.setDatabase(((ElasticCacheReplicationGroupServersConfig) cfg).getDatabase());
		return res;
	}

	private void scheduleReplicationGroupChangeCheck(final ElasticCacheReplicationGroupServersConfig cfg) {
		monitorFuture = GlobalEventExecutor.INSTANCE.schedule(new Runnable() {
			@Override
			public void run() {
				if (isShuttingDown()) {
					return;
				}
				AmazonElastiCache elastiCacheClient = cfg.getAwsElastiCacheClient();
				DescribeReplicationGroupsRequest dccRequest = new DescribeReplicationGroupsRequest();
				dccRequest.setReplicationGroupId(cfg.getReplicationGroupId());
				DescribeReplicationGroupsResult clusterResult = null;
				try {
					clusterResult = elastiCacheClient.describeReplicationGroups(dccRequest);
				} catch (Throwable e) {
					log.error("Error getting nodes from Aws elasticache, we will retry ", e);
					scheduleReplicationGroupChangeCheck(cfg);
					return;
				}
				Set<Endpoint> currentSlaves = new HashSet<>();
				Endpoint primary = null;
				for (NodeGroup group : clusterResult.getReplicationGroups().get(0).getNodeGroups()) {
					for (NodeGroupMember member : group.getNodeGroupMembers()) {
						if (member.getCurrentRole().equals("primary")) {
							primary = member.getReadEndpoint();
						} else {
							currentSlaves.add(member.getReadEndpoint());
						}
					}
				}
				log.info("{} are current slaves", currentSlaves);
				if (isShuttingDown()) {
					return;
				}
				if (primary.equals(currentMaster)) {
					log.debug("Current master {} unchanged", primary);
				} else {
					log.info("Master has changed from {} to {}", currentMaster, primary);
					disconnectedSlaves.add(currentMaster);
					currentMaster = primary;
					changeMaster(singleSlotRange.getStartSlot(), currentMaster.getAddress(), primary.getPort());
				}
				Set<Endpoint> removedCopy = new HashSet<>(slaves);
				Set<Endpoint> addedCopy = new HashSet<>(currentSlaves);

				// Removed
				removedCopy.removeAll(currentSlaves);
				// Newely added
				addedCopy.removeAll(slaves);
				if (!removedCopy.isEmpty() || !addedCopy.isEmpty()) {
					slaves = currentSlaves;
				}

				try {
					if (!removedCopy.isEmpty()) {
						log.info("Slaves  {} removed from replication group", removedCopy);
						for (Endpoint endp : removedCopy) {
							disconnectedSlaves.add(endp);
							slaveDown(singleSlotRange, endp.getAddress(), endp.getPort(), FreezeReason.SYSTEM);
						}
					}
					if (!addedCopy.isEmpty()) {
						log.info("Slaves  {} added to replication group", addedCopy);
						for (Endpoint endp : addedCopy) {
							slaveAdded(endp);
						}
					}
				} catch (Throwable e) {
					log.error("Error updating cache replication entries ", e);
				} finally {
					scheduleReplicationGroupChangeCheck(cfg);
				}
			}
		}, cfg.getScanInterval(), TimeUnit.MILLISECONDS);
	}

	protected void slaveAdded(Endpoint endp) {
		final MasterSlaveEntry entry = getEntry(singleSlotRange.getStartSlot());
		if (disconnectedSlaves.contains(endp)) {
			log.info("adding slave: {} ", endp.getAddress());
			if (entry.slaveUp(endp.getAddress(), endp.getPort(), FreezeReason.MANAGER)) {
				log.info("got true from slaveUp");
				disconnectedSlaves.remove(endp);
			}
		} else {
			RFuture<Void> future = entry.addSlave(endp.getAddress(), endp.getPort());
			future.whenComplete((result,error) -> {
				if(error == null){
					if (entry.slaveUp(endp.getAddress(), endp.getPort(), FreezeReason.MANAGER)) {
						log.info("slave: {} added", endp);
					}
				}else{
					slaves.remove(endp);
					log.error("Can't add slave: " + endp, error);
				}
			});
		}
	}

	@Override
	public void shutdown() {
		monitorFuture.cancel(true);
		super.shutdown();

		for (RedisConnection connection : nodeConnections.values()) {
			connection.getRedisClient().shutdown();
		}
	}

}
