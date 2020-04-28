/*
 * MIT License
 *
 * Copyright (c) 2020 artipie
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.npm.proxy;

import com.artipie.asto.Content;
import com.artipie.npm.proxy.model.NpmAsset;
import com.artipie.npm.proxy.model.NpmPackage;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.vertx.reactivex.core.Vertx;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsSame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

/**
 * Test NPM Proxy works.
 *
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public final class NpmProxyTest {
    /**
     * Last modified date for both package and asset.
     */
    private static final String LAST_MODIFIED = "Tue, 24 Mar 2020 12:15:16 GMT";

    /**
     * Asset Content-Type.
     */
    private static final String DEF_CONTENT_TYPE = "application/octet-stream";

    /**
     * Assert content.
     */
    private static final String DEF_CONTENT = "foobar";

    /**
     * The Vertx instance.
     */
    private static Vertx vertx;

    /**
     * NPM Proxy instance.
     */
    private NpmProxy npm;

    /**
     * Mocked NPM Proxy storage instance.
     */
    @Mock
    private NpmProxyStorage storage;

    /**
     * Mocked NPM Proxy remote client instance.
     */
    @Mock
    private NpmRemote remote;

    @Test
    public void getsPackage() throws IOException {
        final String name = "asdas";
        final NpmPackage expected = defaultPackage();
        Mockito.when(this.remote.loadPackage(name)).thenReturn(Maybe.just(expected));
        Mockito.when(this.storage.save(expected)).thenReturn(Completable.complete());
        MatcherAssert.assertThat(
            this.npm.getPackage(name).blockingGet(),
            new IsSame<>(expected)
        );
        Mockito.verify(this.remote).loadPackage(name);
        Mockito.verify(this.storage).save(expected);
    }

    @Test
    public void getsAsset() throws IOException {
        final String path = "asdas/-/asdas-1.0.0.tgz";
        final NpmAsset loaded = defaultAsset();
        final NpmAsset expected = defaultAsset();
        Mockito.when(this.storage.getAsset(path)).thenAnswer(
            new Answer<Maybe<NpmAsset>>() {
                private boolean first = true;

                @Override
                public Maybe<NpmAsset> answer(final InvocationOnMock invocation) {
                    final Maybe<NpmAsset> result;
                    if (this.first) {
                        this.first = false;
                        result = Maybe.empty();
                    } else {
                        result = Maybe.just(expected);
                    }
                    return result;
                }
            }
        );
        Mockito.when(
            this.remote.loadAsset(Mockito.eq(path), Mockito.any())
        ).thenReturn(Maybe.just(loaded));
        Mockito.when(this.storage.save(loaded)).thenReturn(Completable.complete());
        MatcherAssert.assertThat(
            this.npm.getAsset(path).blockingGet(),
            new IsSame<>(expected)
        );
        Mockito.verify(this.storage, Mockito.times(2)).getAsset(path);
        Mockito.verify(this.remote).loadAsset(Mockito.eq(path), Mockito.any());
        Mockito.verify(this.storage).save(loaded);
    }

    @Test
    public void getsPackageFromCache() throws IOException {
        final String name = "asdas";
        final NpmPackage expected = defaultPackage();
        Mockito.when(this.remote.loadPackage(name)).thenReturn(Maybe.empty());
        Mockito.when(this.storage.getPackage(name)).thenReturn(Maybe.just(expected));
        MatcherAssert.assertThat(
            this.npm.getPackage(name).blockingGet(),
            new IsSame<>(expected)
        );
        Mockito.verify(this.remote).loadPackage(name);
        Mockito.verify(this.storage).getPackage(name);
    }

    @Test
    public void getsAssetFromCache() throws IOException {
        final String path = "asdas/-/asdas-1.0.0.tgz";
        final NpmAsset expected = defaultAsset();
        Mockito.when(this.storage.getAsset(path)).thenReturn(Maybe.just(expected));
        MatcherAssert.assertThat(
            this.npm.getAsset(path).blockingGet(),
            new IsSame<>(expected)
        );
        Mockito.verify(this.storage).getAsset(path);
    }

    @Test
    public void doesNotFindPackage() {
        final String name = "asdas";
        Mockito.when(this.remote.loadPackage(name)).thenReturn(Maybe.empty());
        Mockito.when(this.storage.getPackage(name)).thenReturn(Maybe.empty());
        MatcherAssert.assertThat(
            "Unexpected package found",
            this.npm.getPackage(name).isEmpty().blockingGet()
        );
        Mockito.verify(this.remote).loadPackage(name);
        Mockito.verify(this.storage).getPackage(name);
    }

    @Test
    public void doesNotFindAsset() {
        final String path = "asdas/-/asdas-1.0.0.tgz";
        Mockito.when(this.storage.getAsset(path)).thenReturn(Maybe.empty());
        Mockito.when(
            this.remote.loadAsset(Mockito.eq(path), Mockito.any())
        ).thenReturn(Maybe.empty());
        MatcherAssert.assertThat(
            "Unexpected asset found",
            this.npm.getAsset(path).isEmpty().blockingGet()
        );
        Mockito.verify(this.storage).getAsset(path);
    }

    @BeforeEach
    void setUp() throws IOException {
        this.npm = new NpmProxy(NpmProxyTest.vertx, this.storage, this.remote);
        Mockito.doNothing().when(this.remote).close();
    }

    @AfterEach
    void tearDown() throws IOException {
        this.npm.close();
        Mockito.verify(this.remote).close();
    }

    @BeforeAll
    static void prepare() {
        NpmProxyTest.vertx = Vertx.vertx();
    }

    @AfterAll
    static void cleanup() {
        NpmProxyTest.vertx.close();
    }

    private static NpmPackage defaultPackage() throws IOException {
        return new NpmPackage(
            "asdas",
            IOUtils.resourceToString(
                "/json/cached.json",
                StandardCharsets.UTF_8
            ),
            NpmProxyTest.LAST_MODIFIED
        );
    }

    private static NpmAsset defaultAsset() throws IOException {
        return new NpmAsset(
            "asdas/-/asdas-1.0.0.tgz",
            new Content.From(NpmProxyTest.DEF_CONTENT.getBytes()),
            NpmProxyTest.LAST_MODIFIED,
            NpmProxyTest.DEF_CONTENT_TYPE
        );
    }
}
