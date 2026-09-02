package com.seuprojeto.ecommerce.config;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    // TTL curto de propósito: a contagem de acesso muda a cada visualização
    // de produto, então o cache é "eventualmente consistente" — aceitamos
    // até 5 minutos de defasagem no ranking em troca de não bater no banco
    // a cada requisição da listagem de mais acessados.
    @Bean
    public RedisCacheManagerBuilderCustomizer mostAccessedProductsCacheCustomizer() {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return builder -> builder.withCacheConfiguration("mostAccessedProducts", config);
    }
}
