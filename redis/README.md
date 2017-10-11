<img src="http://ofbiz.apache.org/images/OFBiz-logoV3-apache.png" alt="Apache OFBiz" />

<img src="https://redis.io/images/redis.png" width="150px"/>

# Redis component
This OFBiz component leverages Redis capabilities. This component is developed on Redis 3.2.x and should be compatible with the latest Redis 4.0.x.

If redis.encrypt.password in redis.properties is set, the values stored in Redis will be encrypted.

You can get more info on Redis from https://redis.io/.


# Codis
This component is also compatible with Codis, you can get Codis from https://github.com/CodisLabs/codis.
To use Codis, simply set "address" to Codis proxy port, i.e. 19000 in redis.json:
"address": "redis://127.0.0.1:19000"

# Redisson
Redisson is a Java client of Redis used by this component. It's Apache License V2.0. You can get Redisson from https://github.com/redisson/redisson.
