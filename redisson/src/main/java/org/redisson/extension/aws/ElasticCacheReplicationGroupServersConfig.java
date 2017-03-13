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
import java.util.HashSet;
import java.util.Set;

import org.redisson.config.BaseMasterSlaveServersConfig;

import com.amazonaws.services.elasticache.AmazonElastiCache;


public class ElasticCacheReplicationGroupServersConfig extends BaseMasterSlaveServersConfig<ElasticCacheReplicationGroupServersConfig> {

	private String replicationGroupId;

	private AmazonElastiCache awsElastiCacheClient;

	private int scanInterval = 1000;
	
	/**
     * Redis slave servers addresses
     */
    private Set<URL> slaveAddresses = new HashSet<URL>();

    /**
     * Redis master server address
     */
    private URL masterAddress;

    /**
     * Database index used for Redis connection
     */
    private int database = 0;

	public ElasticCacheReplicationGroupServersConfig() {
	}

	public ElasticCacheReplicationGroupServersConfig setReplicationGroupId(String replicationGroupId) {
		this.replicationGroupId = replicationGroupId;
		return this;
	}

	public ElasticCacheReplicationGroupServersConfig setAmazonElastiCacheClient(AmazonElastiCache awsElastiCacheClient) {
		this.awsElastiCacheClient = awsElastiCacheClient;
		return this;
	}

	public AmazonElastiCache getAwsElastiCacheClient() {
		return awsElastiCacheClient;
	}

	public String getReplicationGroupId() {
		return replicationGroupId;
	}

	public int getScanInterval() {
		return scanInterval;
	}

	public ElasticCacheReplicationGroupServersConfig setScanInterval(int scanInterval) {
		this.scanInterval = scanInterval;
		return this;
	}

	public Set<URL> getSlaveAddresses() {
		return slaveAddresses;
	}

	public void setSlaveAddresses(Set<URL> slaveAddresses) {
		this.slaveAddresses = slaveAddresses;
	}

	public URL getMasterAddress() {
		return masterAddress;
	}

	public void setMasterAddress(URL masterAddress) {
		this.masterAddress = masterAddress;
	}

	public int getDatabase() {
		return database;
	}

	public void setDatabase(int database) {
		this.database = database;
	}

}
