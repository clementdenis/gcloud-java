/*
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.storage;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.api.services.storage.model.StorageObject;
import com.google.cloud.AuthCredentials.ServiceAccountAuthCredentials;
import com.google.cloud.Page;
import com.google.cloud.ReadChannel;
import com.google.cloud.RetryParams;
import com.google.cloud.ServiceOptions;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.Storage.CopyRequest;
import com.google.cloud.storage.spi.StorageRpc;
import com.google.cloud.storage.spi.StorageRpc.Tuple;
import com.google.cloud.storage.spi.StorageRpcFactory;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.io.BaseEncoding;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class StorageImplTest {

  private static final String BUCKET_NAME1 = "b1";
  private static final String BUCKET_NAME2 = "b2";
  private static final String BLOB_NAME1 = "n1";
  private static final String BLOB_NAME2 = "n2";
  private static final String BLOB_NAME3 = "n3";
  private static final byte[] BLOB_CONTENT = {0xD, 0xE, 0xA, 0xD};
  private static final String CONTENT_MD5 = "O1R4G1HJSDUISJjoIYmVhQ==";
  private static final String CONTENT_CRC32C = "9N3EPQ==";
  private static final int DEFAULT_CHUNK_SIZE = 2 * 1024 * 1024;

  // BucketInfo objects
  private static final BucketInfo BUCKET_INFO1 =
      BucketInfo.builder(BUCKET_NAME1).metageneration(42L).build();
  private static final BucketInfo BUCKET_INFO2 = BucketInfo.builder(BUCKET_NAME2).build();

  // BlobInfo objects
  private static final BlobInfo BLOB_INFO1 = BlobInfo.builder(BUCKET_NAME1, BLOB_NAME1, 24L)
      .metageneration(42L).contentType("application/json").md5("md5string").build();
  private static final BlobInfo BLOB_INFO2 = BlobInfo.builder(BUCKET_NAME1, BLOB_NAME2).build();
  private static final BlobInfo BLOB_INFO3 = BlobInfo.builder(BUCKET_NAME1, BLOB_NAME3).build();

  // Empty StorageRpc options
  private static final Map<StorageRpc.Option, ?> EMPTY_RPC_OPTIONS = ImmutableMap.of();

  // Bucket target options
  private static final Storage.BucketTargetOption BUCKET_TARGET_METAGENERATION =
      Storage.BucketTargetOption.metagenerationMatch();
  private static final Storage.BucketTargetOption BUCKET_TARGET_PREDEFINED_ACL =
      Storage.BucketTargetOption.predefinedAcl(Storage.PredefinedAcl.PRIVATE);
  private static final Map<StorageRpc.Option, ?> BUCKET_TARGET_OPTIONS = ImmutableMap.of(
      StorageRpc.Option.IF_METAGENERATION_MATCH, BUCKET_INFO1.metageneration(),
      StorageRpc.Option.PREDEFINED_ACL, BUCKET_TARGET_PREDEFINED_ACL.value());

  // Blob target options (create, update, compose)
  private static final Storage.BlobTargetOption BLOB_TARGET_GENERATION =
      Storage.BlobTargetOption.generationMatch();
  private static final Storage.BlobTargetOption BLOB_TARGET_METAGENERATION =
      Storage.BlobTargetOption.metagenerationMatch();
  private static final Storage.BlobTargetOption BLOB_TARGET_NOT_EXIST =
      Storage.BlobTargetOption.doesNotExist();
  private static final Storage.BlobTargetOption BLOB_TARGET_PREDEFINED_ACL =
      Storage.BlobTargetOption.predefinedAcl(Storage.PredefinedAcl.PRIVATE);
  private static final Map<StorageRpc.Option, ?> BLOB_TARGET_OPTIONS_CREATE = ImmutableMap.of(
      StorageRpc.Option.IF_METAGENERATION_MATCH, BLOB_INFO1.metageneration(),
      StorageRpc.Option.IF_GENERATION_MATCH, 0L,
      StorageRpc.Option.PREDEFINED_ACL, BUCKET_TARGET_PREDEFINED_ACL.value());
  private static final Map<StorageRpc.Option, ?> BLOB_TARGET_OPTIONS_UPDATE = ImmutableMap.of(
      StorageRpc.Option.IF_METAGENERATION_MATCH, BLOB_INFO1.metageneration(),
      StorageRpc.Option.PREDEFINED_ACL, BUCKET_TARGET_PREDEFINED_ACL.value());
  private static final Map<StorageRpc.Option, ?> BLOB_TARGET_OPTIONS_COMPOSE = ImmutableMap.of(
      StorageRpc.Option.IF_GENERATION_MATCH, BLOB_INFO1.generation(),
      StorageRpc.Option.IF_METAGENERATION_MATCH, BLOB_INFO1.metageneration());

  // Blob write options (create, writer)
  private static final Storage.BlobWriteOption BLOB_WRITE_METAGENERATION =
      Storage.BlobWriteOption.metagenerationMatch();
  private static final Storage.BlobWriteOption BLOB_WRITE_NOT_EXIST =
      Storage.BlobWriteOption.doesNotExist();
  private static final Storage.BlobWriteOption BLOB_WRITE_PREDEFINED_ACL =
      Storage.BlobWriteOption.predefinedAcl(Storage.PredefinedAcl.PRIVATE);
  private static final Storage.BlobWriteOption BLOB_WRITE_MD5_HASH =
      Storage.BlobWriteOption.md5Match();
  private static final Storage.BlobWriteOption BLOB_WRITE_CRC2C =
      Storage.BlobWriteOption.crc32cMatch();

  // Bucket get/source options
  private static final Storage.BucketSourceOption BUCKET_SOURCE_METAGENERATION =
      Storage.BucketSourceOption.metagenerationMatch(BUCKET_INFO1.metageneration());
  private static final Map<StorageRpc.Option, ?> BUCKET_SOURCE_OPTIONS = ImmutableMap.of(
      StorageRpc.Option.IF_METAGENERATION_MATCH, BUCKET_SOURCE_METAGENERATION.value());
  private static final Storage.BucketGetOption BUCKET_GET_METAGENERATION =
      Storage.BucketGetOption.metagenerationMatch(BUCKET_INFO1.metageneration());
  private static final Storage.BucketGetOption BUCKET_GET_FIELDS =
      Storage.BucketGetOption.fields(Storage.BucketField.LOCATION, Storage.BucketField.ACL);
  private static final Storage.BucketGetOption BUCKET_GET_EMPTY_FIELDS =
      Storage.BucketGetOption.fields();
  private static final Map<StorageRpc.Option, ?> BUCKET_GET_OPTIONS = ImmutableMap.of(
      StorageRpc.Option.IF_METAGENERATION_MATCH, BUCKET_SOURCE_METAGENERATION.value());

  // Blob get/source options
  private static final Storage.BlobGetOption BLOB_GET_METAGENERATION =
      Storage.BlobGetOption.metagenerationMatch(BLOB_INFO1.metageneration());
  private static final Storage.BlobGetOption BLOB_GET_GENERATION =
      Storage.BlobGetOption.generationMatch(BLOB_INFO1.generation());
  private static final Storage.BlobGetOption BLOB_GET_GENERATION_FROM_BLOB_ID =
      Storage.BlobGetOption.generationMatch();
  private static final Storage.BlobGetOption BLOB_GET_FIELDS =
      Storage.BlobGetOption.fields(Storage.BlobField.CONTENT_TYPE, Storage.BlobField.CRC32C);
  private static final Storage.BlobGetOption BLOB_GET_EMPTY_FIELDS =
      Storage.BlobGetOption.fields();
  private static final Map<StorageRpc.Option, ?> BLOB_GET_OPTIONS = ImmutableMap.of(
      StorageRpc.Option.IF_METAGENERATION_MATCH, BLOB_GET_METAGENERATION.value(),
      StorageRpc.Option.IF_GENERATION_MATCH, BLOB_GET_GENERATION.value());
  private static final Storage.BlobSourceOption BLOB_SOURCE_METAGENERATION =
      Storage.BlobSourceOption.metagenerationMatch(BLOB_INFO1.metageneration());
  private static final Storage.BlobSourceOption BLOB_SOURCE_GENERATION =
      Storage.BlobSourceOption.generationMatch(BLOB_INFO1.generation());
  private static final Storage.BlobSourceOption BLOB_SOURCE_GENERATION_FROM_BLOB_ID =
      Storage.BlobSourceOption.generationMatch();
  private static final Map<StorageRpc.Option, ?> BLOB_SOURCE_OPTIONS = ImmutableMap.of(
      StorageRpc.Option.IF_METAGENERATION_MATCH, BLOB_SOURCE_METAGENERATION.value(),
      StorageRpc.Option.IF_GENERATION_MATCH, BLOB_SOURCE_GENERATION.value());
  private static final Map<StorageRpc.Option, ?> BLOB_SOURCE_OPTIONS_COPY = ImmutableMap.of(
      StorageRpc.Option.IF_SOURCE_METAGENERATION_MATCH, BLOB_SOURCE_METAGENERATION.value(),
      StorageRpc.Option.IF_SOURCE_GENERATION_MATCH, BLOB_SOURCE_GENERATION.value());

  // Bucket list options
  private static final Storage.BucketListOption BUCKET_LIST_PAGE_SIZE =
      Storage.BucketListOption.pageSize(42L);
  private static final Storage.BucketListOption BUCKET_LIST_PREFIX =
      Storage.BucketListOption.prefix("prefix");
  private static final Storage.BucketListOption BUCKET_LIST_FIELDS =
      Storage.BucketListOption.fields(Storage.BucketField.LOCATION, Storage.BucketField.ACL);
  private static final Storage.BucketListOption BUCKET_LIST_EMPTY_FIELDS =
      Storage.BucketListOption.fields();
  private static final Map<StorageRpc.Option, ?> BUCKET_LIST_OPTIONS = ImmutableMap.of(
      StorageRpc.Option.MAX_RESULTS, BUCKET_LIST_PAGE_SIZE.value(),
      StorageRpc.Option.PREFIX, BUCKET_LIST_PREFIX.value());

  // Blob list options
  private static final Storage.BlobListOption BLOB_LIST_PAGE_SIZE =
      Storage.BlobListOption.pageSize(42L);
  private static final Storage.BlobListOption BLOB_LIST_PREFIX =
      Storage.BlobListOption.prefix("prefix");
  private static final Storage.BlobListOption BLOB_LIST_FIELDS =
      Storage.BlobListOption.fields(Storage.BlobField.CONTENT_TYPE, Storage.BlobField.MD5HASH);
  private static final Storage.BlobListOption BLOB_LIST_VERSIONS =
      Storage.BlobListOption.versions(false);
  private static final Storage.BlobListOption BLOB_LIST_EMPTY_FIELDS =
      Storage.BlobListOption.fields();
  private static final Map<StorageRpc.Option, ?> BLOB_LIST_OPTIONS = ImmutableMap.of(
      StorageRpc.Option.MAX_RESULTS, BLOB_LIST_PAGE_SIZE.value(),
      StorageRpc.Option.PREFIX, BLOB_LIST_PREFIX.value(),
      StorageRpc.Option.VERSIONS, BLOB_LIST_VERSIONS.value());

  private static final String PRIVATE_KEY_STRING = "MIICdwIBADANBgkqhkiG9w0BAQEFAASCAmEwggJdAgEAAoG"
          + "BAL2xolH1zrISQ8+GzOV29BNjjzq4/HIP8Psd1+cZb81vDklSF+95wB250MSE0BDc81pvIMwj5OmIfLg1NY6uB"
          + "1xavOPpVdx1z664AGc/BEJ1zInXGXaQ6s+SxGenVq40Yws57gikQGMZjttpf1Qbz4DjkxsbRoeaRHn06n9pH1e"
          + "jAgMBAAECgYEAkWcm0AJF5LMhbWKbjkxm/LG06UNApkHX6vTOOOODkonM/qDBnhvKCj8Tan+PaU2j7679Cd19q"
          + "xCm4SBQJET7eBhqLD9L2j9y0h2YUQnLbISaqUS1/EXcr2C1Lf9VCEn1y/GYuDYqs85rGoQ4ZYfM9ClROSq86fH"
          + "+cbIIssqJqukCQQD18LjfJz/ichFeli5/l1jaFid2XoCH3T6TVuuysszVx68fh60gSIxEF/0X2xB+wuPxTP4IQ"
          + "+t8tD/ktd232oWXAkEAxXPych2QBHePk9/lek4tOkKBgfnDzex7S/pI0G1vpB3VmzBbCsokn9lpOv7JV8071GD"
          + "lW/7R6jlLfpQy3hN31QJAE10osSk99m5Uv8XDU3hvHnywDrnSFOBulNs7I47AYfSe7TSZhPkxUgsxejddTR27J"
          + "LyTI8N1PxRSE4feNSOXcQJAMMKJRJT4U6IS2rmXubREhvaVdLtxFxEnAYQ1JwNfZm/XqBMw6GEy2iaeTetNXVl"
          + "ZRQEIoscyn1y2v/No/F5iYQJBAKBOGASoQcBjGTOg/H/SfcE8QVNsKEpthRrs6CkpT80aZ/AV+ksfoIf2zw2M3"
          + "mAHfrO+TBLdz4sicuFQvlN9SEc=";

  private static final String PUBLIC_KEY_STRING = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC9saJR9c6y"
          + "EkPPhszldvQTY486uPxyD/D7HdfnGW/Nbw5JUhfvecAdudDEhNAQ3PNabyDMI+TpiHy4NTWOrgdcWrzj6VXcdc"
          + "+uuABnPwRCdcyJ1xl2kOrPksRnp1auNGMLOe4IpEBjGY7baX9UG8+A45MbG0aHmkR59Op/aR9XowIDAQAB";

  private static final ServiceOptions.Clock TIME_SOURCE = new ServiceOptions.Clock() {
    @Override
    public long millis() {
      return 42000L;
    }
  };

  private static final String ACCOUNT = "account";
  private static PrivateKey privateKey;
  private static PublicKey publicKey;

  private StorageOptions options;
  private StorageRpcFactory rpcFactoryMock;
  private StorageRpc storageRpcMock;
  private Storage storage;

  private Blob expectedBlob1, expectedBlob2, expectedBlob3;
  private Bucket expectedBucket1, expectedBucket2;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @BeforeClass
  public static void beforeClass() throws NoSuchAlgorithmException, InvalidKeySpecException {
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    EncodedKeySpec privateKeySpec =
        new PKCS8EncodedKeySpec(BaseEncoding.base64().decode(PRIVATE_KEY_STRING));
    privateKey = keyFactory.generatePrivate(privateKeySpec);
    EncodedKeySpec publicKeySpec =
        new X509EncodedKeySpec(BaseEncoding.base64().decode(PUBLIC_KEY_STRING));
    publicKey = keyFactory.generatePublic(publicKeySpec);
  }

  @Before
  public void setUp() {
    rpcFactoryMock = EasyMock.createMock(StorageRpcFactory.class);
    storageRpcMock = EasyMock.createMock(StorageRpc.class);
    EasyMock.expect(rpcFactoryMock.create(EasyMock.anyObject(StorageOptions.class)))
        .andReturn(storageRpcMock);
    EasyMock.replay(rpcFactoryMock);
    options = StorageOptions.builder()
        .projectId("projectId")
        .clock(TIME_SOURCE)
        .serviceRpcFactory(rpcFactoryMock)
        .retryParams(RetryParams.noRetries())
        .build();
  }

  @After
  public void tearDown() throws Exception {
    EasyMock.verify(rpcFactoryMock, storageRpcMock);
  }

  private void initializeService() {
    storage = options.service();
    initializeServiceDependentObjects();
  }

  private void initializeServiceDependentObjects() {
    expectedBlob1 = new Blob(storage, new BlobInfo.BuilderImpl(BLOB_INFO1));
    expectedBlob2 = new Blob(storage, new BlobInfo.BuilderImpl(BLOB_INFO2));
    expectedBlob3 = new Blob(storage, new BlobInfo.BuilderImpl(BLOB_INFO3));
    expectedBucket1 = new Bucket(storage, new BucketInfo.BuilderImpl(BUCKET_INFO1));
    expectedBucket2 = new Bucket(storage, new BucketInfo.BuilderImpl(BUCKET_INFO2));
  }

  @Test
  public void testGetOptions() {
    EasyMock.replay(storageRpcMock);
    initializeService();
    assertSame(options, storage.options());
  }

  @Test
  public void testCreateBucket() {
    EasyMock.expect(storageRpcMock.create(BUCKET_INFO1.toPb(), EMPTY_RPC_OPTIONS))
        .andReturn(BUCKET_INFO1.toPb());
    EasyMock.replay(storageRpcMock);
    initializeService();
    Bucket bucket = storage.create(BUCKET_INFO1);
    assertEquals(expectedBucket1, bucket);
  }

  @Test
  public void testCreateBucketWithOptions() {
    EasyMock.expect(storageRpcMock.create(BUCKET_INFO1.toPb(), BUCKET_TARGET_OPTIONS))
        .andReturn(BUCKET_INFO1.toPb());
    EasyMock.replay(storageRpcMock);
    initializeService();
    Bucket bucket =
        storage.create(BUCKET_INFO1, BUCKET_TARGET_METAGENERATION, BUCKET_TARGET_PREDEFINED_ACL);
    assertEquals(expectedBucket1, bucket);
  }

  @Test
  public void testCreateBlob() throws IOException {
    Capture<ByteArrayInputStream> capturedStream = Capture.newInstance();
    EasyMock.expect(storageRpcMock.create(
        EasyMock.eq(BLOB_INFO1.toBuilder().md5(CONTENT_MD5).crc32c(CONTENT_CRC32C).build().toPb()),
        EasyMock.capture(capturedStream),
        EasyMock.eq(EMPTY_RPC_OPTIONS)))
        .andReturn(BLOB_INFO1.toPb());
    EasyMock.replay(storageRpcMock);
    initializeService();
    Blob blob = storage.create(BLOB_INFO1, BLOB_CONTENT);
    assertEquals(expectedBlob1, blob);
    ByteArrayInputStream byteStream = capturedStream.getValue();
    byte[] streamBytes = new byte[BLOB_CONTENT.length];
    assertEquals(BLOB_CONTENT.length, byteStream.read(streamBytes));
    assertArrayEquals(BLOB_CONTENT, streamBytes);
    assertEquals(-1, byteStream.read(streamBytes));
  }

  @Test
  public void testCreateEmptyBlob() throws IOException {
    Capture<ByteArrayInputStream> capturedStream = Capture.newInstance();
    EasyMock.expect(storageRpcMock.create(
        EasyMock.eq(BLOB_INFO1.toBuilder()
            .md5("1B2M2Y8AsgTpgAmY7PhCfg==")
            .crc32c("AAAAAA==")
            .build()
            .toPb()),
        EasyMock.capture(capturedStream),
        EasyMock.eq(EMPTY_RPC_OPTIONS)))
        .andReturn(BLOB_INFO1.toPb());
    EasyMock.replay(storageRpcMock);
    initializeService();
    Blob blob = storage.create(BLOB_INFO1);
    assertEquals(expectedBlob1, blob);
    ByteArrayInputStream byteStream = capturedStream.getValue();
    byte[] streamBytes = new byte[BLOB_CONTENT.length];
    assertEquals(-1, byteStream.read(streamBytes));
  }

  @Test
  public void testCreateBlobWithOptions() throws IOException {
    Capture<ByteArrayInputStream> capturedStream = Capture.newInstance();
    EasyMock.expect(storageRpcMock.create(
        EasyMock.eq(BLOB_INFO1.toBuilder()
            .md5(CONTENT_MD5)
            .crc32c(CONTENT_CRC32C)
            .build()
            .toPb()),
        EasyMock.capture(capturedStream),
        EasyMock.eq(BLOB_TARGET_OPTIONS_CREATE)))
        .andReturn(BLOB_INFO1.toPb());
    EasyMock.replay(storageRpcMock);
    initializeService();
    Blob blob =
        storage.create(BLOB_INFO1, BLOB_CONTENT, BLOB_TARGET_METAGENERATION, BLOB_TARGET_NOT_EXIST,
            BLOB_TARGET_PREDEFINED_ACL);
    assertEquals(expectedBlob1, blob);
    ByteArrayInputStream byteStream = capturedStream.getValue();
    byte[] streamBytes = new byte[BLOB_CONTENT.length];
    assertEquals(BLOB_CONTENT.length, byteStream.read(streamBytes));
    assertArrayEquals(BLOB_CONTENT, streamBytes);
    assertEquals(-1, byteStream.read(streamBytes));
  }

  @Test
  public void testCreateBlobFromStream() {
    ByteArrayInputStream fileStream = new ByteArrayInputStream(BLOB_CONTENT);
    BlobInfo.Builder infoBuilder = BLOB_INFO1.toBuilder();
    BlobInfo infoWithHashes = infoBuilder.md5(CONTENT_MD5).crc32c(CONTENT_CRC32C).build();
    BlobInfo infoWithoutHashes = infoBuilder.md5(null).crc32c(null).build();
    EasyMock.expect(storageRpcMock.create(infoWithoutHashes.toPb(), fileStream, EMPTY_RPC_OPTIONS))
        .andReturn(BLOB_INFO1.toPb());
    EasyMock.replay(storageRpcMock);
    initializeService();
    Blob blob = storage.create(infoWithHashes, fileStream);
    assertEquals(expectedBlob1, blob);
  }

  @Test
  public void testGetBucket() {
    EasyMock.expect(storageRpcMock.get(BucketInfo.of(BUCKET_NAME1).toPb(), EMPTY_RPC_OPTIONS))
        .andReturn(BUCKET_INFO1.toPb());
    EasyMock.replay(storageRpcMock);
    initializeService();
    Bucket bucket = storage.get(BUCKET_NAME1);
    assertEquals(expectedBucket1, bucket);
  }

  @Test
  public void testGetBucketWithOptions() {
    EasyMock.expect(storageRpcMock.get(BucketInfo.of(BUCKET_NAME1).toPb(), BUCKET_GET_OPTIONS))
        .andReturn(BUCKET_INFO1.toPb());
    EasyMock.replay(storageRpcMock);
    initializeService();
    Bucket bucket = storage.get(BUCKET_NAME1, BUCKET_GET_METAGENERATION);
    assertEquals(expectedBucket1, bucket);
  }

  @Test
  public void testGetBucketWithSelectedFields() {
    Capture<Map<StorageRpc.Option, Object>> capturedOptions = Capture.newInstance();
    EasyMock.expect(storageRpcMock.get(EasyMock.eq(BucketInfo.of(BUCKET_NAME1).toPb()),
        EasyMock.capture(capturedOptions))).andReturn(BUCKET_INFO1.toPb());
    EasyMock.replay(storageRpcMock);
    initializeService();
    Bucket bucket = storage.get(BUCKET_NAME1, BUCKET_GET_METAGENERATION, BUCKET_GET_FIELDS);
    assertEquals(BUCKET_GET_METAGENERATION.value(),
        capturedOptions.getValue().get(BUCKET_GET_METAGENERATION.rpcOption()));
    String selector = (String) capturedOptions.getValue().get(BLOB_GET_FIELDS.rpcOption());
    assertTrue(selector.contains("name"));
    assertTrue(selector.contains("location"));
    assertTrue(selector.contains("acl"));
    assertEquals(17, selector.length());
    assertEquals(BUCKET_INFO1.name(), bucket.name());
  }

  @Test
  public void testGetBucketWithEmptyFields() {
    Capture<Map<StorageRpc.Option, Object>> capturedOptions = Capture.newInstance();
    EasyMock.expect(storageRpcMock.get(EasyMock.eq(BucketInfo.of(BUCKET_NAME1).toPb()),
        EasyMock.capture(capturedOptions))).andReturn(BUCKET_INFO1.toPb());
    EasyMock.replay(storageRpcMock);
    initializeService();
    Bucket bucket = storage.get(BUCKET_NAME1, BUCKET_GET_METAGENERATION,
        BUCKET_GET_EMPTY_FIELDS);
    assertEquals(BUCKET_GET_METAGENERATION.value(),
        capturedOptions.getValue().get(BUCKET_GET_METAGENERATION.rpcOption()));
    String selector = (String) capturedOptions.getValue().get(BLOB_GET_FIELDS.rpcOption());
    assertTrue(selector.contains("name"));
    assertEquals(4, selector.length());
    assertEquals(BUCKET_INFO1.name(), bucket.name());
  }

  @Test
  public void testGetBlob() {
    EasyMock.expect(
        storageRpcMock.get(BlobId.of(BUCKET_NAME1, BLOB_NAME1).toPb(), EMPTY_RPC_OPTIONS))
        .andReturn(BLOB_INFO1.toPb());
    EasyMock.replay(storageRpcMock);
    initializeService();
    Blob blob = storage.get(BUCKET_NAME1, BLOB_NAME1);
    assertEquals(expectedBlob1, blob);
  }

  @Test
  public void testGetBlobWithOptions() {
    EasyMock.expect(
        storageRpcMock.get(BlobId.of(BUCKET_NAME1, BLOB_NAME1).toPb(), BLOB_GET_OPTIONS))
        .andReturn(BLOB_INFO1.toPb());
    EasyMock.replay(storageRpcMock);
    initializeService();
    Blob blob =
        storage.get(BUCKET_NAME1, BLOB_NAME1, BLOB_GET_METAGENERATION, BLOB_GET_GENERATION);
    assertEquals(expectedBlob1, blob);
  }

  @Test
  public void testGetBlobWithOptionsFromBlobId() {
    EasyMock.expect(
        storageRpcMock.get(BLOB_INFO1.blobId().toPb(), BLOB_GET_OPTIONS))
        .andReturn(BLOB_INFO1.toPb());
    EasyMock.replay(storageRpcMock);
    initializeService();
    Blob blob =
        storage.get(BLOB_INFO1.blobId(), BLOB_GET_METAGENERATION, BLOB_GET_GENERATION_FROM_BLOB_ID);
    assertEquals(expectedBlob1, blob);
  }

  @Test
  public void testGetBlobWithSelectedFields() {
    Capture<Map<StorageRpc.Option, Object>> capturedOptions = Capture.newInstance();
    EasyMock.expect(storageRpcMock.get(EasyMock.eq(BlobId.of(BUCKET_NAME1, BLOB_NAME1).toPb()),
        EasyMock.capture(capturedOptions))).andReturn(BLOB_INFO1.toPb());
    EasyMock.replay(storageRpcMock);
    initializeService();
    Blob blob = storage.get(
        BUCKET_NAME1, BLOB_NAME1, BLOB_GET_METAGENERATION,
        BLOB_GET_GENERATION, BLOB_GET_FIELDS);
    assertEquals(BLOB_GET_METAGENERATION.value(),
        capturedOptions.getValue().get(BLOB_GET_METAGENERATION.rpcOption()));
    assertEquals(BLOB_GET_GENERATION.value(),
        capturedOptions.getValue().get(BLOB_GET_GENERATION.rpcOption()));
    String selector = (String) capturedOptions.getValue().get(BLOB_GET_FIELDS.rpcOption());
    assertTrue(selector.contains("bucket"));
    assertTrue(selector.contains("name"));
    assertTrue(selector.contains("contentType"));
    assertTrue(selector.contains("crc32c"));
    assertEquals(30, selector.length());
    assertEquals(expectedBlob1, blob);
  }

  @Test
  public void testGetBlobWithEmptyFields() {
    Capture<Map<StorageRpc.Option, Object>> capturedOptions = Capture.newInstance();
    EasyMock.expect(storageRpcMock.get(EasyMock.eq(BlobId.of(BUCKET_NAME1, BLOB_NAME1).toPb()),
        EasyMock.capture(capturedOptions))).andReturn(BLOB_INFO1.toPb());
    EasyMock.replay(storageRpcMock);
    initializeService();
    Blob blob = storage.get(BUCKET_NAME1, BLOB_NAME1, BLOB_GET_METAGENERATION,
        BLOB_GET_GENERATION, BLOB_GET_EMPTY_FIELDS);
    assertEquals(BLOB_GET_METAGENERATION.value(),
        capturedOptions.getValue().get(BLOB_GET_METAGENERATION.rpcOption()));
    assertEquals(BLOB_GET_GENERATION.value(),
        capturedOptions.getValue().get(BLOB_GET_GENERATION.rpcOption()));
    String selector = (String) capturedOptions.getValue().get(BLOB_GET_FIELDS.rpcOption());
    assertTrue(selector.contains("bucket"));
    assertTrue(selector.contains("name"));
    assertEquals(11, selector.length());
    assertEquals(expectedBlob1, blob);
  }

  @Test
  public void testListBuckets() {
    String cursor = "cursor";
    ImmutableList<BucketInfo> bucketInfoList = ImmutableList.of(BUCKET_INFO1, BUCKET_INFO2);
    Tuple<String, Iterable<com.google.api.services.storage.model.Bucket>> result =
        Tuple.of(cursor, Iterables.transform(bucketInfoList, BucketInfo.TO_PB_FUNCTION));
    EasyMock.expect(storageRpcMock.list(EMPTY_RPC_OPTIONS)).andReturn(result);
    EasyMock.replay(storageRpcMock);
    initializeService();
    ImmutableList<Bucket> bucketList = ImmutableList.of(expectedBucket1, expectedBucket2);
    Page<Bucket> page = storage.list();
    assertEquals(cursor, page.nextPageCursor());
    assertArrayEquals(bucketList.toArray(), Iterables.toArray(page.values(), Bucket.class));
  }

  @Test
  public void testListBucketsEmpty() {
    EasyMock.expect(storageRpcMock.list(EMPTY_RPC_OPTIONS)).andReturn(
        Tuple.<String, Iterable<com.google.api.services.storage.model.Bucket>>of(null, null));
    EasyMock.replay(storageRpcMock);
    initializeService();
    Page<Bucket> page = storage.list();
    assertNull(page.nextPageCursor());
    assertArrayEquals(ImmutableList.of().toArray(), Iterables.toArray(page.values(), Bucket.class));
  }

  @Test
  public void testListBucketsWithOptions() {
    String cursor = "cursor";
    ImmutableList<BucketInfo> bucketInfoList = ImmutableList.of(BUCKET_INFO1, BUCKET_INFO2);
    Tuple<String, Iterable<com.google.api.services.storage.model.Bucket>> result =
        Tuple.of(cursor, Iterables.transform(bucketInfoList, BucketInfo.TO_PB_FUNCTION));
    EasyMock.expect(storageRpcMock.list(BUCKET_LIST_OPTIONS)).andReturn(result);
    EasyMock.replay(storageRpcMock);
    initializeService();
    ImmutableList<Bucket> bucketList = ImmutableList.of(expectedBucket1, expectedBucket2);
    Page<Bucket> page = storage.list(BUCKET_LIST_PAGE_SIZE, BUCKET_LIST_PREFIX);
    assertEquals(cursor, page.nextPageCursor());
    assertArrayEquals(bucketList.toArray(), Iterables.toArray(page.values(), Bucket.class));
  }

  @Test
  public void testListBucketsWithSelectedFields() {
    String cursor = "cursor";
    Capture<Map<StorageRpc.Option, Object>> capturedOptions = Capture.newInstance();
    ImmutableList<BucketInfo> bucketInfoList = ImmutableList.of(BUCKET_INFO1, BUCKET_INFO2);
    Tuple<String, Iterable<com.google.api.services.storage.model.Bucket>> result =
        Tuple.of(cursor, Iterables.transform(bucketInfoList, BucketInfo.TO_PB_FUNCTION));
    EasyMock.expect(storageRpcMock.list(EasyMock.capture(capturedOptions))).andReturn(result);
    EasyMock.replay(storageRpcMock);
    initializeService();
    ImmutableList<Bucket> bucketList = ImmutableList.of(expectedBucket1, expectedBucket2);
    Page<Bucket> page = storage.list(BUCKET_LIST_FIELDS);
    String selector = (String) capturedOptions.getValue().get(BUCKET_LIST_FIELDS.rpcOption());
    assertTrue(selector.contains("items("));
    assertTrue(selector.contains("name"));
    assertTrue(selector.contains("acl"));
    assertTrue(selector.contains("location"));
    assertTrue(selector.contains("nextPageToken"));
    assertTrue(selector.endsWith(")"));
    assertEquals(38, selector.length());
    assertEquals(cursor, page.nextPageCursor());
    assertArrayEquals(bucketList.toArray(), Iterables.toArray(page.values(), Bucket.class));
  }

  @Test
  public void testListBucketsWithEmptyFields() {
    String cursor = "cursor";
    Capture<Map<StorageRpc.Option, Object>> capturedOptions = Capture.newInstance();
    ImmutableList<BucketInfo> bucketInfoList = ImmutableList.of(BUCKET_INFO1, BUCKET_INFO2);
    Tuple<String, Iterable<com.google.api.services.storage.model.Bucket>> result =
        Tuple.of(cursor, Iterables.transform(bucketInfoList, BucketInfo.TO_PB_FUNCTION));
    EasyMock.expect(storageRpcMock.list(EasyMock.capture(capturedOptions))).andReturn(result);
    EasyMock.replay(storageRpcMock);
    initializeService();
    ImmutableList<Bucket> bucketList = ImmutableList.of(expectedBucket1, expectedBucket2);
    Page<Bucket> page = storage.list(BUCKET_LIST_EMPTY_FIELDS);
    String selector = (String) capturedOptions.getValue().get(BUCKET_LIST_EMPTY_FIELDS.rpcOption());
    assertTrue(selector.contains("items("));
    assertTrue(selector.contains("name"));
    assertTrue(selector.contains("nextPageToken"));
    assertTrue(selector.endsWith(")"));
    assertEquals(25, selector.length());
    assertEquals(cursor, page.nextPageCursor());
    assertArrayEquals(bucketList.toArray(), Iterables.toArray(page.values(), Bucket.class));
  }

  @Test
  public void testListBlobs() {
    String cursor = "cursor";
    ImmutableList<BlobInfo> blobInfoList = ImmutableList.of(BLOB_INFO1, BLOB_INFO2);
    Tuple<String, Iterable<com.google.api.services.storage.model.StorageObject>> result =
        Tuple.of(cursor, Iterables.transform(blobInfoList, BlobInfo.INFO_TO_PB_FUNCTION));
    EasyMock.expect(storageRpcMock.list(BUCKET_NAME1, EMPTY_RPC_OPTIONS)).andReturn(result);
    EasyMock.replay(storageRpcMock);
    initializeService();
    ImmutableList<Blob> blobList = ImmutableList.of(expectedBlob1, expectedBlob2);
    Page<Blob> page = storage.list(BUCKET_NAME1);
    assertEquals(cursor, page.nextPageCursor());
    assertArrayEquals(blobList.toArray(), Iterables.toArray(page.values(), Blob.class));
  }

  @Test
  public void testListBlobsEmpty() {
    EasyMock.expect(storageRpcMock.list(BUCKET_NAME1, EMPTY_RPC_OPTIONS))
        .andReturn(Tuple.<String, Iterable<com.google.api.services.storage.model.StorageObject>>of(
            null, null));
    EasyMock.replay(storageRpcMock);
    initializeService();
    Page<Blob> page = storage.list(BUCKET_NAME1);
    assertNull(page.nextPageCursor());
    assertArrayEquals(ImmutableList.of().toArray(), Iterables.toArray(page.values(), Blob.class));
  }

  @Test
  public void testListBlobsWithOptions() {
    String cursor = "cursor";
    ImmutableList<BlobInfo> blobInfoList = ImmutableList.of(BLOB_INFO1, BLOB_INFO2);
    Tuple<String, Iterable<com.google.api.services.storage.model.StorageObject>> result =
        Tuple.of(cursor, Iterables.transform(blobInfoList, BlobInfo.INFO_TO_PB_FUNCTION));
    EasyMock.expect(storageRpcMock.list(BUCKET_NAME1, BLOB_LIST_OPTIONS)).andReturn(result);
    EasyMock.replay(storageRpcMock);
    initializeService();
    ImmutableList<Blob> blobList = ImmutableList.of(expectedBlob1, expectedBlob2);
    Page<Blob> page =
        storage.list(BUCKET_NAME1, BLOB_LIST_PAGE_SIZE, BLOB_LIST_PREFIX, BLOB_LIST_VERSIONS);
    assertEquals(cursor, page.nextPageCursor());
    assertArrayEquals(blobList.toArray(), Iterables.toArray(page.values(), Blob.class));
  }

  @Test
  public void testListBlobsWithSelectedFields() {
    String cursor = "cursor";
    Capture<Map<StorageRpc.Option, Object>> capturedOptions = Capture.newInstance();
    ImmutableList<BlobInfo> blobInfoList = ImmutableList.of(BLOB_INFO1, BLOB_INFO2);
    Tuple<String, Iterable<com.google.api.services.storage.model.StorageObject>> result =
        Tuple.of(cursor, Iterables.transform(blobInfoList, BlobInfo.INFO_TO_PB_FUNCTION));
    EasyMock.expect(
        storageRpcMock.list(EasyMock.eq(BUCKET_NAME1), EasyMock.capture(capturedOptions)))
        .andReturn(result);
    EasyMock.replay(storageRpcMock);
    initializeService();
    ImmutableList<Blob> blobList = ImmutableList.of(expectedBlob1, expectedBlob2);
    Page<Blob> page =
        storage.list(BUCKET_NAME1, BLOB_LIST_PAGE_SIZE, BLOB_LIST_PREFIX, BLOB_LIST_FIELDS);
    assertEquals(BLOB_LIST_PAGE_SIZE.value(),
        capturedOptions.getValue().get(BLOB_LIST_PAGE_SIZE.rpcOption()));
    assertEquals(BLOB_LIST_PREFIX.value(),
        capturedOptions.getValue().get(BLOB_LIST_PREFIX.rpcOption()));
    String selector = (String) capturedOptions.getValue().get(BLOB_LIST_FIELDS.rpcOption());
    assertTrue(selector.contains("prefixes"));
    assertTrue(selector.contains("items("));
    assertTrue(selector.contains("bucket"));
    assertTrue(selector.contains("name"));
    assertTrue(selector.contains("contentType"));
    assertTrue(selector.contains("md5Hash"));
    assertTrue(selector.contains("nextPageToken"));
    assertTrue(selector.endsWith(")"));
    assertEquals(61, selector.length());
    assertEquals(cursor, page.nextPageCursor());
    assertArrayEquals(blobList.toArray(), Iterables.toArray(page.values(), Blob.class));
  }

  @Test
  public void testListBlobsWithEmptyFields() {
    String cursor = "cursor";
    Capture<Map<StorageRpc.Option, Object>> capturedOptions = Capture.newInstance();
    ImmutableList<BlobInfo> blobInfoList = ImmutableList.of(BLOB_INFO1, BLOB_INFO2);
    Tuple<String, Iterable<com.google.api.services.storage.model.StorageObject>> result =
        Tuple.of(cursor, Iterables.transform(blobInfoList, BlobInfo.INFO_TO_PB_FUNCTION));
    EasyMock.expect(
        storageRpcMock.list(EasyMock.eq(BUCKET_NAME1), EasyMock.capture(capturedOptions)))
        .andReturn(result);
    EasyMock.replay(storageRpcMock);
    initializeService();
    ImmutableList<Blob> blobList = ImmutableList.of(expectedBlob1, expectedBlob2);
    Page<Blob> page =
        storage.list(BUCKET_NAME1, BLOB_LIST_PAGE_SIZE, BLOB_LIST_PREFIX, BLOB_LIST_EMPTY_FIELDS);
    assertEquals(BLOB_LIST_PAGE_SIZE.value(),
        capturedOptions.getValue().get(BLOB_LIST_PAGE_SIZE.rpcOption()));
    assertEquals(BLOB_LIST_PREFIX.value(),
        capturedOptions.getValue().get(BLOB_LIST_PREFIX.rpcOption()));
    String selector = (String) capturedOptions.getValue().get(BLOB_LIST_EMPTY_FIELDS.rpcOption());
    assertTrue(selector.contains("prefixes"));
    assertTrue(selector.contains("items("));
    assertTrue(selector.contains("bucket"));
    assertTrue(selector.contains("name"));
    assertTrue(selector.contains("nextPageToken"));
    assertTrue(selector.endsWith(")"));
    assertEquals(41, selector.length());
    assertEquals(cursor, page.nextPageCursor());
    assertArrayEquals(blobList.toArray(), Iterables.toArray(page.values(), Blob.class));
  }

  @Test
  public void testListBlobsCurrentDirectory() {
    String cursor = "cursor";
    Map<StorageRpc.Option, ?> options = ImmutableMap.of(StorageRpc.Option.DELIMITER, "/");
    ImmutableList<BlobInfo> blobInfoList = ImmutableList.of(BLOB_INFO1, BLOB_INFO2);
    Tuple<String, Iterable<com.google.api.services.storage.model.StorageObject>> result =
        Tuple.of(cursor, Iterables.transform(blobInfoList, BlobInfo.INFO_TO_PB_FUNCTION));
    EasyMock.expect(storageRpcMock.list(BUCKET_NAME1, options)).andReturn(result);
    EasyMock.replay(storageRpcMock);
    initializeService();
    ImmutableList<Blob> blobList = ImmutableList.of(expectedBlob1, expectedBlob2);
    Page<Blob> page = storage.list(BUCKET_NAME1, Storage.BlobListOption.currentDirectory());
    assertEquals(cursor, page.nextPageCursor());
    assertArrayEquals(blobList.toArray(), Iterables.toArray(page.values(), Blob.class));
  }

  @Test
  public void testUpdateBucket() {
    BucketInfo updatedBucketInfo = BUCKET_INFO1.toBuilder().indexPage("some-page").build();
    EasyMock.expect(storageRpcMock.patch(updatedBucketInfo.toPb(), EMPTY_RPC_OPTIONS))
        .andReturn(updatedBucketInfo.toPb());
    EasyMock.replay(storageRpcMock);
    initializeService();
    Bucket bucket = storage.update(updatedBucketInfo);
    assertEquals(new Bucket(storage, new BucketInfo.BuilderImpl(updatedBucketInfo)), bucket);
  }

  @Test
  public void testUpdateBucketWithOptions() {
    BucketInfo updatedBucketInfo = BUCKET_INFO1.toBuilder().indexPage("some-page").build();
    EasyMock.expect(storageRpcMock.patch(updatedBucketInfo.toPb(), BUCKET_TARGET_OPTIONS))
        .andReturn(updatedBucketInfo.toPb());
    EasyMock.replay(storageRpcMock);
    initializeService();
    Bucket bucket =
        storage.update(updatedBucketInfo, BUCKET_TARGET_METAGENERATION,
            BUCKET_TARGET_PREDEFINED_ACL);
    assertEquals(new Bucket(storage, new BucketInfo.BuilderImpl(updatedBucketInfo)), bucket);
  }

  @Test
  public void testUpdateBlob() {
    BlobInfo updatedBlobInfo = BLOB_INFO1.toBuilder().contentType("some-content-type").build();
    EasyMock.expect(storageRpcMock.patch(updatedBlobInfo.toPb(), EMPTY_RPC_OPTIONS))
        .andReturn(updatedBlobInfo.toPb());
    EasyMock.replay(storageRpcMock);
    initializeService();
    Blob blob = storage.update(updatedBlobInfo);
    assertEquals(new Blob(storage, new BlobInfo.BuilderImpl(updatedBlobInfo)), blob);
  }

  @Test
  public void testUpdateBlobWithOptions() {
    BlobInfo updatedBlobInfo = BLOB_INFO1.toBuilder().contentType("some-content-type").build();
    EasyMock.expect(storageRpcMock.patch(updatedBlobInfo.toPb(), BLOB_TARGET_OPTIONS_UPDATE))
        .andReturn(updatedBlobInfo.toPb());
    EasyMock.replay(storageRpcMock);
    initializeService();
    Blob blob =
        storage.update(updatedBlobInfo, BLOB_TARGET_METAGENERATION, BLOB_TARGET_PREDEFINED_ACL);
    assertEquals(new Blob(storage, new BlobInfo.BuilderImpl(updatedBlobInfo)), blob);
  }

  @Test
  public void testDeleteBucket() {
    EasyMock.expect(storageRpcMock.delete(BucketInfo.of(BUCKET_NAME1).toPb(), EMPTY_RPC_OPTIONS))
        .andReturn(true);
    EasyMock.replay(storageRpcMock);
    initializeService();
    assertTrue(storage.delete(BUCKET_NAME1));
  }

  @Test
  public void testDeleteBucketWithOptions() {
    EasyMock
        .expect(storageRpcMock.delete(BucketInfo.of(BUCKET_NAME1).toPb(), BUCKET_SOURCE_OPTIONS))
        .andReturn(true);
    EasyMock.replay(storageRpcMock);
    initializeService();
    assertTrue(storage.delete(BUCKET_NAME1, BUCKET_SOURCE_METAGENERATION));
  }

  @Test
  public void testDeleteBlob() {
    EasyMock.expect(
        storageRpcMock.delete(BlobId.of(BUCKET_NAME1, BLOB_NAME1).toPb(), EMPTY_RPC_OPTIONS))
        .andReturn(true);
    EasyMock.replay(storageRpcMock);
    initializeService();
    assertTrue(storage.delete(BUCKET_NAME1, BLOB_NAME1));
  }

  @Test
  public void testDeleteBlobWithOptions() {
    EasyMock.expect(
        storageRpcMock.delete(BlobId.of(BUCKET_NAME1, BLOB_NAME1).toPb(), BLOB_SOURCE_OPTIONS))
        .andReturn(true);
    EasyMock.replay(storageRpcMock);
    initializeService();
    assertTrue(storage.delete(BUCKET_NAME1, BLOB_NAME1, BLOB_SOURCE_GENERATION,
        BLOB_SOURCE_METAGENERATION));
  }

  @Test
  public void testDeleteBlobWithOptionsFromBlobId() {
    EasyMock.expect(
        storageRpcMock.delete(BLOB_INFO1.blobId().toPb(), BLOB_SOURCE_OPTIONS))
        .andReturn(true);
    EasyMock.replay(storageRpcMock);
    initializeService();
    assertTrue(storage.delete(BLOB_INFO1.blobId(), BLOB_SOURCE_GENERATION_FROM_BLOB_ID,
        BLOB_SOURCE_METAGENERATION));
  }

  @Test
  public void testCompose() {
    Storage.ComposeRequest req = Storage.ComposeRequest.builder()
        .addSource(BLOB_NAME2, BLOB_NAME3)
        .target(BLOB_INFO1)
        .build();
    EasyMock.expect(storageRpcMock.compose(ImmutableList.of(BLOB_INFO2.toPb(), BLOB_INFO3.toPb()),
        BLOB_INFO1.toPb(), EMPTY_RPC_OPTIONS)).andReturn(BLOB_INFO1.toPb());
    EasyMock.replay(storageRpcMock);
    initializeService();
    Blob blob = storage.compose(req);
    assertEquals(expectedBlob1, blob);
  }

  @Test
  public void testComposeWithOptions() {
    Storage.ComposeRequest req = Storage.ComposeRequest.builder()
        .addSource(BLOB_NAME2, BLOB_NAME3)
        .target(BLOB_INFO1)
        .targetOptions(BLOB_TARGET_GENERATION, BLOB_TARGET_METAGENERATION)
        .build();
    EasyMock.expect(storageRpcMock.compose(ImmutableList.of(BLOB_INFO2.toPb(), BLOB_INFO3.toPb()),
        BLOB_INFO1.toPb(), BLOB_TARGET_OPTIONS_COMPOSE)).andReturn(BLOB_INFO1.toPb());
    EasyMock.replay(storageRpcMock);
    initializeService();
    Blob blob = storage.compose(req);
    assertEquals(expectedBlob1, blob);
  }

  @Test
  public void testCopy() {
    CopyRequest request = Storage.CopyRequest.of(BLOB_INFO1.blobId(), BLOB_INFO2.blobId());
    StorageRpc.RewriteRequest rpcRequest = new StorageRpc.RewriteRequest(request.source().toPb(),
        EMPTY_RPC_OPTIONS, false, BLOB_INFO2.toPb(), EMPTY_RPC_OPTIONS, null);
    StorageRpc.RewriteResponse rpcResponse = new StorageRpc.RewriteResponse(rpcRequest, null, 42L,
        false, "token", 21L);
    EasyMock.expect(storageRpcMock.openRewrite(rpcRequest)).andReturn(rpcResponse);
    EasyMock.replay(storageRpcMock);
    initializeService();
    CopyWriter writer = storage.copy(request);
    assertEquals(42L, writer.blobSize());
    assertEquals(21L, writer.totalBytesCopied());
    assertTrue(!writer.isDone());
  }

  @Test
  public void testCopyWithOptions() {
    CopyRequest request = Storage.CopyRequest.builder()
        .source(BLOB_INFO2.blobId())
        .sourceOptions(BLOB_SOURCE_GENERATION, BLOB_SOURCE_METAGENERATION)
        .target(BLOB_INFO1, BLOB_TARGET_GENERATION, BLOB_TARGET_METAGENERATION)
        .build();
    StorageRpc.RewriteRequest rpcRequest = new StorageRpc.RewriteRequest(request.source().toPb(),
        BLOB_SOURCE_OPTIONS_COPY, true, request.target().toPb(), BLOB_TARGET_OPTIONS_COMPOSE, null);
    StorageRpc.RewriteResponse rpcResponse = new StorageRpc.RewriteResponse(rpcRequest, null, 42L,
        false, "token", 21L);
    EasyMock.expect(storageRpcMock.openRewrite(rpcRequest)).andReturn(rpcResponse);
    EasyMock.replay(storageRpcMock);
    initializeService();
    CopyWriter writer = storage.copy(request);
    assertEquals(42L, writer.blobSize());
    assertEquals(21L, writer.totalBytesCopied());
    assertTrue(!writer.isDone());
  }

  @Test
  public void testCopyWithOptionsFromBlobId() {
    CopyRequest request = Storage.CopyRequest.builder()
        .source(BLOB_INFO1.blobId())
        .sourceOptions(BLOB_SOURCE_GENERATION_FROM_BLOB_ID, BLOB_SOURCE_METAGENERATION)
        .target(BLOB_INFO1, BLOB_TARGET_GENERATION, BLOB_TARGET_METAGENERATION)
        .build();
    StorageRpc.RewriteRequest rpcRequest = new StorageRpc.RewriteRequest(request.source().toPb(),
        BLOB_SOURCE_OPTIONS_COPY, true, request.target().toPb(), BLOB_TARGET_OPTIONS_COMPOSE, null);
    StorageRpc.RewriteResponse rpcResponse =
        new StorageRpc.RewriteResponse(rpcRequest, null, 42L, false, "token", 21L);
    EasyMock.expect(storageRpcMock.openRewrite(rpcRequest)).andReturn(rpcResponse);
    EasyMock.replay(storageRpcMock);
    initializeService();
    CopyWriter writer = storage.copy(request);
    assertEquals(42L, writer.blobSize());
    assertEquals(21L, writer.totalBytesCopied());
    assertTrue(!writer.isDone());
  }

  @Test
  public void testCopyMultipleRequests() {
    CopyRequest request = Storage.CopyRequest.of(BLOB_INFO1.blobId(), BLOB_INFO2.blobId());
    StorageRpc.RewriteRequest rpcRequest = new StorageRpc.RewriteRequest(request.source().toPb(),
        EMPTY_RPC_OPTIONS, false, BLOB_INFO2.toPb(), EMPTY_RPC_OPTIONS, null);
    StorageRpc.RewriteResponse rpcResponse1 = new StorageRpc.RewriteResponse(rpcRequest, null, 42L,
        false, "token", 21L);
    StorageRpc.RewriteResponse rpcResponse2 = new StorageRpc.RewriteResponse(rpcRequest,
        BLOB_INFO1.toPb(), 42L, true, "token", 42L);
    EasyMock.expect(storageRpcMock.openRewrite(rpcRequest)).andReturn(rpcResponse1);
    EasyMock.expect(storageRpcMock.continueRewrite(rpcResponse1)).andReturn(rpcResponse2);
    EasyMock.replay(storageRpcMock);
    initializeService();
    CopyWriter writer = storage.copy(request);
    assertEquals(42L, writer.blobSize());
    assertEquals(21L, writer.totalBytesCopied());
    assertTrue(!writer.isDone());
    assertEquals(expectedBlob1, writer.result());
    assertTrue(writer.isDone());
    assertEquals(42L, writer.totalBytesCopied());
    assertEquals(42L, writer.blobSize());
  }

  @Test
  public void testReadAllBytes() {
    EasyMock.expect(
        storageRpcMock.load(BlobId.of(BUCKET_NAME1, BLOB_NAME1).toPb(), EMPTY_RPC_OPTIONS))
        .andReturn(BLOB_CONTENT);
    EasyMock.replay(storageRpcMock);
    initializeService();
    byte[] readBytes = storage.readAllBytes(BUCKET_NAME1, BLOB_NAME1);
    assertArrayEquals(BLOB_CONTENT, readBytes);
  }

  @Test
  public void testReadAllBytesWithOptions() {
    EasyMock.expect(
        storageRpcMock.load(BlobId.of(BUCKET_NAME1, BLOB_NAME1).toPb(), BLOB_SOURCE_OPTIONS))
        .andReturn(BLOB_CONTENT);
    EasyMock.replay(storageRpcMock);
    initializeService();
    byte[] readBytes = storage.readAllBytes(BUCKET_NAME1, BLOB_NAME1, BLOB_SOURCE_GENERATION,
        BLOB_SOURCE_METAGENERATION);
    assertArrayEquals(BLOB_CONTENT, readBytes);
  }

  @Test
  public void testReadAllBytesWithOptionsFromBlobId() {
    EasyMock.expect(
        storageRpcMock.load(BLOB_INFO1.blobId().toPb(), BLOB_SOURCE_OPTIONS))
        .andReturn(BLOB_CONTENT);
    EasyMock.replay(storageRpcMock);
    initializeService();
    byte[] readBytes = storage.readAllBytes(BLOB_INFO1.blobId(),
        BLOB_SOURCE_GENERATION_FROM_BLOB_ID, BLOB_SOURCE_METAGENERATION);
    assertArrayEquals(BLOB_CONTENT, readBytes);
  }

  @Test
  public void testApply() {
    BatchRequest req = BatchRequest.builder()
        .delete(BUCKET_NAME1, BLOB_NAME1)
        .update(BLOB_INFO2)
        .get(BUCKET_NAME1, BLOB_NAME3)
        .build();
    List<StorageObject> toDelete = ImmutableList.of(BlobId.of(BUCKET_NAME1, BLOB_NAME1).toPb());
    List<StorageObject> toUpdate = ImmutableList.of(BlobId.of(BUCKET_NAME1, BLOB_NAME2).toPb());
    List<StorageObject> toGet = ImmutableList.of(BlobId.of(BUCKET_NAME1, BLOB_NAME3).toPb());
    List<Map<StorageRpc.Option, ?>> deleteOptions =
        ImmutableList.<Map<StorageRpc.Option, ?>>of(EMPTY_RPC_OPTIONS);
    List<Map<StorageRpc.Option, ?>> updateOptions =
        ImmutableList.<Map<StorageRpc.Option, ?>>of(EMPTY_RPC_OPTIONS);
    List<Map<StorageRpc.Option, ?>> getOptions =
        ImmutableList.<Map<StorageRpc.Option, ?>>of(EMPTY_RPC_OPTIONS);

    Map<StorageObject, Tuple<Boolean, StorageException>> deleteResult = Maps.toMap(toDelete,
        new Function<StorageObject, Tuple<Boolean, StorageException>>() {
          @Override
          public Tuple<Boolean, StorageException> apply(StorageObject f) {
            return Tuple.of(true, null);
          }
        });
    Map<StorageObject, Tuple<StorageObject, StorageException>> updateResult = Maps.toMap(toUpdate,
        new Function<StorageObject, Tuple<StorageObject, StorageException>>() {
          @Override
          public Tuple<StorageObject, StorageException> apply(StorageObject f) {
            return Tuple.of(f, null);
          }
        });
    Map<StorageObject, Tuple<StorageObject, StorageException>> getResult = Maps.toMap(toGet,
        new Function<StorageObject, Tuple<StorageObject, StorageException>>() {
          @Override
          public Tuple<StorageObject, StorageException> apply(StorageObject f) {
            return Tuple.of(f, null);
          }
        });
    StorageRpc.BatchResponse res =
        new StorageRpc.BatchResponse(deleteResult, updateResult, getResult);

    Capture<StorageRpc.BatchRequest> capturedBatchRequest = Capture.newInstance();
    EasyMock.expect(storageRpcMock.batch(EasyMock.capture(capturedBatchRequest))).andReturn(res);
    EasyMock.replay(storageRpcMock);
    initializeService();
    BatchResponse batchResponse = storage.submit(req);

    // Verify captured StorageRpc.BatchRequest
    List<Tuple<StorageObject, Map<StorageRpc.Option, ?>>> capturedToDelete =
        capturedBatchRequest.getValue().toDelete;
    List<Tuple<StorageObject, Map<StorageRpc.Option, ?>>> capturedToUpdate =
        capturedBatchRequest.getValue().toUpdate;
    List<Tuple<StorageObject, Map<StorageRpc.Option, ?>>> capturedToGet =
        capturedBatchRequest.getValue().toGet;
    for (int i = 0; i < capturedToDelete.size(); i++) {
      assertEquals(toDelete.get(i), capturedToDelete.get(i).x());
      assertEquals(deleteOptions.get(i), capturedToDelete.get(i).y());
    }
    for (int i = 0; i < capturedToUpdate.size(); i++) {
      assertEquals(toUpdate.get(i), capturedToUpdate.get(i).x());
      assertEquals(updateOptions.get(i), capturedToUpdate.get(i).y());
    }
    for (int i = 0; i < capturedToGet.size(); i++) {
      assertEquals(toGet.get(i), capturedToGet.get(i).x());
      assertEquals(getOptions.get(i), capturedToGet.get(i).y());
    }

    // Verify BatchResponse
    for (BatchResponse.Result<Boolean> result : batchResponse.deletes()) {
      assertTrue(result.get());
    }
    for (int i = 0; i < batchResponse.updates().size(); i++) {
      assertEquals(toUpdate.get(i), batchResponse.updates().get(i).get().toPb());
    }
    for (int i = 0; i < batchResponse.gets().size(); i++) {
      assertEquals(toGet.get(i), batchResponse.gets().get(i).get().toPb());
    }
  }

  @Test
  public void testReader() {
    EasyMock.replay(storageRpcMock);
    initializeService();
    ReadChannel channel = storage.reader(BUCKET_NAME1, BLOB_NAME1);
    assertNotNull(channel);
    assertTrue(channel.isOpen());
  }

  @Test
  public void testReaderWithOptions() throws IOException {
    byte[] result = new byte[DEFAULT_CHUNK_SIZE];
    EasyMock.expect(
        storageRpcMock.read(BLOB_INFO2.toPb(), BLOB_SOURCE_OPTIONS, 0, DEFAULT_CHUNK_SIZE))
        .andReturn(StorageRpc.Tuple.of("etag", result));
    EasyMock.replay(storageRpcMock);
    initializeService();
    ReadChannel channel = storage.reader(BUCKET_NAME1, BLOB_NAME2, BLOB_SOURCE_GENERATION,
        BLOB_SOURCE_METAGENERATION);
    assertNotNull(channel);
    assertTrue(channel.isOpen());
    channel.read(ByteBuffer.allocate(42));
  }

  @Test
  public void testReaderWithOptionsFromBlobId() throws IOException {
    byte[] result = new byte[DEFAULT_CHUNK_SIZE];
    EasyMock.expect(
        storageRpcMock.read(BLOB_INFO1.blobId().toPb(), BLOB_SOURCE_OPTIONS, 0, DEFAULT_CHUNK_SIZE))
        .andReturn(StorageRpc.Tuple.of("etag", result));
    EasyMock.replay(storageRpcMock);
    initializeService();
    ReadChannel channel = storage.reader(BLOB_INFO1.blobId(),
        BLOB_SOURCE_GENERATION_FROM_BLOB_ID, BLOB_SOURCE_METAGENERATION);
    assertNotNull(channel);
    assertTrue(channel.isOpen());
    channel.read(ByteBuffer.allocate(42));
  }

  @Test
  public void testWriter() {
    BlobInfo.Builder infoBuilder = BLOB_INFO1.toBuilder();
    BlobInfo infoWithHashes = infoBuilder.md5(CONTENT_MD5).crc32c(CONTENT_CRC32C).build();
    BlobInfo infoWithoutHashes = infoBuilder.md5(null).crc32c(null).build();
    EasyMock.expect(storageRpcMock.open(infoWithoutHashes.toPb(), EMPTY_RPC_OPTIONS))
        .andReturn("upload-id");
    EasyMock.replay(storageRpcMock);
    initializeService();
    WriteChannel channel = storage.writer(infoWithHashes);
    assertNotNull(channel);
    assertTrue(channel.isOpen());
  }

  @Test
  public void testWriterWithOptions() {
    BlobInfo info = BLOB_INFO1.toBuilder().md5(CONTENT_MD5).crc32c(CONTENT_CRC32C).build();
    EasyMock.expect(storageRpcMock.open(info.toPb(), BLOB_TARGET_OPTIONS_CREATE))
        .andReturn("upload-id");
    EasyMock.replay(storageRpcMock);
    initializeService();
    WriteChannel channel = storage.writer(info, BLOB_WRITE_METAGENERATION, BLOB_WRITE_NOT_EXIST,
        BLOB_WRITE_PREDEFINED_ACL, BLOB_WRITE_CRC2C, BLOB_WRITE_MD5_HASH);
    assertNotNull(channel);
    assertTrue(channel.isOpen());
  }

  @Test
  public void testSignUrl() throws NoSuchAlgorithmException, InvalidKeyException,
      SignatureException, UnsupportedEncodingException {
    EasyMock.replay(storageRpcMock);
    ServiceAccountAuthCredentials authCredentials =
        ServiceAccountAuthCredentials.createFor(ACCOUNT, privateKey);
    storage = options.toBuilder().authCredentials(authCredentials).build().service();
    URL url = storage.signUrl(BLOB_INFO1, 14, TimeUnit.DAYS);
    String stringUrl = url.toString();
    String expectedUrl =
        new StringBuilder("https://storage.googleapis.com/").append(BUCKET_NAME1).append("/")
            .append(BLOB_NAME1).append("?GoogleAccessId=").append(ACCOUNT).append("&Expires=")
            .append(42L + 1209600).append("&Signature=").toString();
    assertTrue(stringUrl.startsWith(expectedUrl));
    String signature = stringUrl.substring(expectedUrl.length());

    StringBuilder signedMessageBuilder = new StringBuilder();
    signedMessageBuilder.append(HttpMethod.GET).append('\n').append('\n').append('\n')
        .append(42L + 1209600).append('\n').append("/").append(BUCKET_NAME1).append("/")
        .append(BLOB_NAME1);

    Signature signer = Signature.getInstance("SHA256withRSA");
    signer.initVerify(publicKey);
    signer.update(signedMessageBuilder.toString().getBytes(UTF_8));
    assertTrue(signer.verify(BaseEncoding.base64().decode(
        URLDecoder.decode(signature, UTF_8.name()))));
  }

  @Test
  public void testSignUrlWithOptions() throws NoSuchAlgorithmException, InvalidKeyException,
      SignatureException, UnsupportedEncodingException {
    EasyMock.replay(storageRpcMock);
    ServiceAccountAuthCredentials authCredentials =
        ServiceAccountAuthCredentials.createFor(ACCOUNT, privateKey);
    storage = options.toBuilder().authCredentials(authCredentials).build().service();
    URL url =
        storage.signUrl(BLOB_INFO1, 14, TimeUnit.DAYS,
            Storage.SignUrlOption.httpMethod(HttpMethod.POST),
            Storage.SignUrlOption.withContentType(), Storage.SignUrlOption.withMd5());
    String stringUrl = url.toString();
    String expectedUrl =
        new StringBuilder("https://storage.googleapis.com/").append(BUCKET_NAME1).append("/")
            .append(BLOB_NAME1).append("?GoogleAccessId=").append(ACCOUNT).append("&Expires=")
            .append(42L + 1209600).append("&Signature=").toString();
    assertTrue(stringUrl.startsWith(expectedUrl));
    String signature = stringUrl.substring(expectedUrl.length());

    StringBuilder signedMessageBuilder = new StringBuilder();
    signedMessageBuilder.append(HttpMethod.POST).append('\n').append(BLOB_INFO1.md5()).append('\n')
        .append(BLOB_INFO1.contentType()).append('\n').append(42L + 1209600).append('\n')
        .append("/").append(BUCKET_NAME1).append("/").append(BLOB_NAME1);

    Signature signer = Signature.getInstance("SHA256withRSA");
    signer.initVerify(publicKey);
    signer.update(signedMessageBuilder.toString().getBytes(UTF_8));
    assertTrue(signer.verify(BaseEncoding.base64().decode(
        URLDecoder.decode(signature, UTF_8.name()))));
  }

  @Test
  public void testGetAll() {
    BlobId blobId1 = BlobId.of(BUCKET_NAME1, BLOB_NAME1);
    BlobId blobId2 = BlobId.of(BUCKET_NAME1, BLOB_NAME2);
    StorageObject storageObject1 = blobId1.toPb();
    StorageObject storageObject2 = blobId2.toPb();
    List<StorageObject> toGet = ImmutableList.of(storageObject1, storageObject2);

    Map<StorageObject, Tuple<Boolean, StorageException>> deleteResult = ImmutableMap.of();
    Map<StorageObject, Tuple<StorageObject, StorageException>> updateResult = ImmutableMap.of();
    Map<StorageObject, Tuple<StorageObject, StorageException>> getResult = Maps.toMap(toGet,
        new Function<StorageObject, Tuple<StorageObject, StorageException>>() {
          @Override
          public Tuple<StorageObject, StorageException> apply(StorageObject f) {
            return Tuple.of(f, null);
          }
        });
    StorageRpc.BatchResponse res =
        new StorageRpc.BatchResponse(deleteResult, updateResult, getResult);

    Capture<StorageRpc.BatchRequest> capturedBatchRequest = Capture.newInstance();
    EasyMock.expect(storageRpcMock.batch(EasyMock.capture(capturedBatchRequest))).andReturn(res);
    EasyMock.replay(storageRpcMock);
    initializeService();
    List<Blob> resultBlobs = storage.get(blobId1, blobId2);

    // Verify captured StorageRpc.BatchRequest
    List<Tuple<StorageObject, Map<StorageRpc.Option, ?>>> capturedToGet =
        capturedBatchRequest.getValue().toGet;
    assertTrue(capturedBatchRequest.getValue().toDelete.isEmpty());
    assertTrue(capturedBatchRequest.getValue().toUpdate.isEmpty());
    for (int i = 0; i < capturedToGet.size(); i++) {
      assertEquals(toGet.get(i), capturedToGet.get(i).x());
      assertTrue(capturedToGet.get(i).y().isEmpty());
    }

    // Verify result
    for (int i = 0; i < resultBlobs.size(); i++) {
      assertEquals(toGet.get(i), resultBlobs.get(i).toPb());
    }
  }

  @Test
  public void testUpdateAll() {
    BlobInfo blobInfo1 = BlobInfo.builder(BUCKET_NAME1, BLOB_NAME1).contentType("type").build();
    BlobInfo blobInfo2 = BlobInfo.builder(BUCKET_NAME1, BLOB_NAME2).contentType("type").build();
    StorageObject storageObject1 = blobInfo1.toPb();
    StorageObject storageObject2 = blobInfo2.toPb();
    List<StorageObject> toUpdate = ImmutableList.of(storageObject1, storageObject2);

    Map<StorageObject, Tuple<Boolean, StorageException>> deleteResult = ImmutableMap.of();
    Map<StorageObject, Tuple<StorageObject, StorageException>> getResult = ImmutableMap.of();
    Map<StorageObject, Tuple<StorageObject, StorageException>> updateResult = Maps.toMap(toUpdate,
        new Function<StorageObject, Tuple<StorageObject, StorageException>>() {
          @Override
          public Tuple<StorageObject, StorageException> apply(StorageObject f) {
            return Tuple.of(f, null);
          }
        });
    StorageRpc.BatchResponse res =
        new StorageRpc.BatchResponse(deleteResult, updateResult, getResult);

    Capture<StorageRpc.BatchRequest> capturedBatchRequest = Capture.newInstance();
    EasyMock.expect(storageRpcMock.batch(EasyMock.capture(capturedBatchRequest))).andReturn(res);
    EasyMock.replay(storageRpcMock);
    initializeService();
    List<Blob> resultBlobs = storage.update(blobInfo1, blobInfo2);

    // Verify captured StorageRpc.BatchRequest
    List<Tuple<StorageObject, Map<StorageRpc.Option, ?>>> capturedToUpdate =
        capturedBatchRequest.getValue().toUpdate;
    assertTrue(capturedBatchRequest.getValue().toDelete.isEmpty());
    assertTrue(capturedBatchRequest.getValue().toGet.isEmpty());
    for (int i = 0; i < capturedToUpdate.size(); i++) {
      assertEquals(toUpdate.get(i), capturedToUpdate.get(i).x());
      assertTrue(capturedToUpdate.get(i).y().isEmpty());
    }

    // Verify result
    for (int i = 0; i < resultBlobs.size(); i++) {
      assertEquals(toUpdate.get(i), resultBlobs.get(i).toPb());
    }
  }

  @Test
  public void testDeleteAll() {
    BlobInfo blobInfo1 = BlobInfo.builder(BUCKET_NAME1, BLOB_NAME1).build();
    BlobInfo blobInfo2 = BlobInfo.builder(BUCKET_NAME1, BLOB_NAME2).build();
    StorageObject storageObject1 = blobInfo1.toPb();
    StorageObject storageObject2 = blobInfo2.toPb();
    List<StorageObject> toUpdate = ImmutableList.of(storageObject1, storageObject2);

    Map<StorageObject, Tuple<StorageObject, StorageException>> updateResult = ImmutableMap.of();
    Map<StorageObject, Tuple<StorageObject, StorageException>> getResult = ImmutableMap.of();
    Map<StorageObject, Tuple<Boolean, StorageException>> deleteResult = Maps.toMap(toUpdate,
        new Function<StorageObject, Tuple<Boolean, StorageException>>() {
          @Override
          public Tuple<Boolean, StorageException> apply(StorageObject f) {
            return Tuple.of(true, null);
          }
        });
    StorageRpc.BatchResponse res =
        new StorageRpc.BatchResponse(deleteResult, updateResult, getResult);

    Capture<StorageRpc.BatchRequest> capturedBatchRequest = Capture.newInstance();
    EasyMock.expect(storageRpcMock.batch(EasyMock.capture(capturedBatchRequest))).andReturn(res);
    EasyMock.replay(storageRpcMock);
    initializeService();
    List<Boolean> deleteResults = storage.delete(blobInfo1.blobId(), blobInfo2.blobId());

    // Verify captured StorageRpc.BatchRequest
    List<Tuple<StorageObject, Map<StorageRpc.Option, ?>>> capturedToDelete =
        capturedBatchRequest.getValue().toDelete;
    assertTrue(capturedBatchRequest.getValue().toUpdate.isEmpty());
    assertTrue(capturedBatchRequest.getValue().toGet.isEmpty());
    for (int i = 0; i < capturedToDelete.size(); i++) {
      assertEquals(toUpdate.get(i), capturedToDelete.get(i).x());
      assertTrue(capturedToDelete.get(i).y().isEmpty());
    }

    // Verify result
    for (Boolean deleteStatus : deleteResults) {
      assertTrue(deleteStatus);
    }
  }

  @Test
  public void testRetryableException() {
    BlobId blob = BlobId.of(BUCKET_NAME1, BLOB_NAME1);
    EasyMock.expect(storageRpcMock.get(blob.toPb(), EMPTY_RPC_OPTIONS))
        .andThrow(new StorageException(500, "internalError"))
        .andReturn(BLOB_INFO1.toPb());
    EasyMock.replay(storageRpcMock);
    storage = options.toBuilder().retryParams(RetryParams.defaultInstance()).build().service();
    initializeServiceDependentObjects();
    Blob readBlob = storage.get(blob);
    assertEquals(expectedBlob1, readBlob);
  }

  @Test
  public void testNonRetryableException() {
    BlobId blob = BlobId.of(BUCKET_NAME1, BLOB_NAME1);
    String exceptionMessage = "Not Implemented";
    EasyMock.expect(storageRpcMock.get(blob.toPb(), EMPTY_RPC_OPTIONS))
        .andThrow(new StorageException(501, exceptionMessage));
    EasyMock.replay(storageRpcMock);
    storage = options.toBuilder().retryParams(RetryParams.defaultInstance()).build().service();
    initializeServiceDependentObjects();
    thrown.expect(StorageException.class);
    thrown.expectMessage(exceptionMessage);
    storage.get(blob);
  }

  @Test
  public void testRuntimeException() {
    BlobId blob = BlobId.of(BUCKET_NAME1, BLOB_NAME1);
    String exceptionMessage = "Artificial runtime exception";
    EasyMock.expect(storageRpcMock.get(blob.toPb(), EMPTY_RPC_OPTIONS))
        .andThrow(new RuntimeException(exceptionMessage));
    EasyMock.replay(storageRpcMock);
    storage = options.toBuilder().retryParams(RetryParams.defaultInstance()).build().service();
    thrown.expect(StorageException.class);
    thrown.expectMessage(exceptionMessage);
    storage.get(blob);
  }
}
