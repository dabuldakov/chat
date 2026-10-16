package com.chat.server.conversation.gif;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GifCatalogRepository extends JpaRepository<GifCatalog, Long> {

    Optional<GifCatalog> findByProviderAndProviderGifId(String provider, String providerGifId);

    Optional<GifCatalog> findByGifUuid(java.util.UUID gifUuid);

    List<GifCatalog> findByGifIdIn(Collection<Long> gifIds);
}
