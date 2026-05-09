/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
package org.redisson.api;

/**
 * Floating-point homogeneous array precision type for JSON.SET FPHA argument.
 * Requires <b>Redis 8.8.0 or higher.</b>
 *
 * @author Triet Nguyen
 */
public enum FPHAType {
    /** Brain Float 16-bit precision. */
    BF16,
    /** 16-bit floating-point precision. */
    FP16,
    /** 32-bit floating-point precision. */
    FP32,
    /** 64-bit floating-point precision. */
    FP64
}
