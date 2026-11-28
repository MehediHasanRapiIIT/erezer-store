package kn.org.deliverybackend.integration.invoiceapp;

import kn.org.deliverybackend.entity.StoreSettings;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.Change;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.Settings;
import kn.org.deliverybackend.repository.StoreSettingsRepository;
import kn.org.deliverybackend.util.SecretBox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Optional;

/**
 * Settings → Erezer Invoice: where it is and the key it gave us. The key is
 * stored locked and is never sent back to the panel, only its last letters.
 */
@Service
@RequiredArgsConstructor
public class InvoiceAppSettingsService {

    private final StoreSettingsRepository repository;
    private final SecretBox secretBox;

    /** Where to send, with the key, once both are saved. */
    public record Connection(String url, String key) {
    }

    @Transactional(readOnly = true)
    public Settings get() {
        StoreSettings s = repository.findById(StoreSettings.SINGLETON_ID).orElse(null);
        String url = s == null ? null : blankToNull(s.getInvoiceAppUrl());
        String key = s == null ? null : secretBox.unlock(s.getInvoiceAppKey());
        boolean keySaved = key != null && !key.isBlank();
        return new Settings(url, keySaved, SecretBox.hint(key), url != null && keySaved);
    }

    @Transactional
    public Settings update(Change change) {
        StoreSettings s = repository.findById(StoreSettings.SINGLETON_ID)
                .orElseThrow(() -> new InvalidRequestException("Store settings are not ready yet."));
        if (change.url() != null) s.setInvoiceAppUrl(normalise(change.url()));
        if (Boolean.TRUE.equals(change.removeKey())) {
            s.setInvoiceAppKey(null);
        } else if (change.key() != null && !change.key().isBlank()) {
            String key = change.key().trim();
            if (!key.matches("^ezs_[A-Za-z0-9_-]{20,}$")) {
                throw new InvalidRequestException(
                        "That isn't an Erezer Invoice shop key. Copy it from Erezer Invoice → Shop connection (it starts with ezs_).");
            }
            s.setInvoiceAppKey(secretBox.lock(key));
        }
        repository.save(s);
        return get();
    }

    @Transactional(readOnly = true)
    public Optional<Connection> connection() {
        StoreSettings s = repository.findById(StoreSettings.SINGLETON_ID).orElse(null);
        if (s == null || blankToNull(s.getInvoiceAppUrl()) == null) return Optional.empty();
        String key = secretBox.unlock(s.getInvoiceAppKey());
        if (key == null || key.isBlank()) return Optional.empty();
        return Optional.of(new Connection(s.getInvoiceAppUrl(), key));
    }

    /** "https://invoice.erezer.com/" → "https://invoice.erezer.com"; empty → null. Only http(s) with a host. */
    static String normalise(String raw) {
        String url = raw.trim();
        if (url.isEmpty()) return null;
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getRawUserInfo() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Type Erezer Invoice's address as it opens in the browser, e.g. https://invoice.erezer.com");
        }
        return url;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
