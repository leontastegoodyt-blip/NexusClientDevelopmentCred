package com.nexusclient.backend.screenshots;

import com.nexusclient.backend.NexusException;
import com.nexusclient.backend.account.SignedInCheck;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * ScreenshotController – the Screenshot Uploader mod (press F2 in game, the link is copied).
 *   POST /api/screenshots          signed in; the body is the PNG file (max 8 MB, 30 a day)
 *                                  → {"id": "k7Qp2xZa9mRt", "url": "/s/k7Qp2xZa9mRt.png"}
 *   GET  /s/{id}.png               public: the picture (anyone with the link can see it)
 * The id is random and long, so nobody can guess other people's links.
 */
@RestController
public class ScreenshotController {

    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int PER_DAY = 30;
    private static final String ID_LETTERS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final byte[] PNG_START = {(byte) 0x89, 'P', 'N', 'G'};

    private final JdbcClient db;
    private final SecureRandom random = new SecureRandom();

    public ScreenshotController(JdbcClient db) {
        this.db = db;
    }

    @PostMapping(value = "/api/screenshots", consumes = MediaType.IMAGE_PNG_VALUE)
    public Map<String, String> upload(@RequestAttribute(SignedInCheck.ME) UUID me, @RequestBody byte[] image) {
        if (image.length == 0 || image.length > MAX_BYTES) {
            throw NexusException.badRequest("Screenshots can be up to 8 MB");
        }
        for (int i = 0; i < PNG_START.length; i++) {
            if (image.length < PNG_START.length || image[i] != PNG_START[i]) {
                throw NexusException.badRequest("That isn't a PNG picture");
            }
        }
        long today = db.sql("select count(*) from screenshots where player_uuid = ? and created_at > ?")
                .param(me).param(Timestamp.from(Instant.now().minus(Duration.ofDays(1)))).query(Long.class).single();
        if (today >= PER_DAY) {
            throw NexusException.badRequest("That's " + PER_DAY + " screenshots today – try again tomorrow");
        }
        String id = newId();
        db.sql("insert into screenshots (id, player_uuid, image, created_at) values (?, ?, ?, ?)")
                .param(id).param(me).param(image).param(Timestamp.from(Instant.now())).update();
        return Map.of("id", id, "url", "/s/" + id + ".png");
    }

    @GetMapping("/s/{id}.png")
    public ResponseEntity<byte[]> view(@PathVariable String id) {
        if (!id.matches("[A-Za-z0-9]{12}")) {
            throw NexusException.notFound("No such screenshot");
        }
        Optional<byte[]> image = db.sql("select image from screenshots where id = ?").param(id)
                .query(byte[].class).optional();
        return image.map(bytes -> ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS)).body(bytes))
                .orElseThrow(() -> NexusException.notFound("No such screenshot"));
    }

    private String newId() {
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            id.append(ID_LETTERS.charAt(random.nextInt(ID_LETTERS.length())));
        }
        return id.toString();
    }
}
