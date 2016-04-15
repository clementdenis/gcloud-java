/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.gcloud.compute.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableSet;
import com.google.gcloud.Page;
import com.google.gcloud.compute.Address;
import com.google.gcloud.compute.AddressId;
import com.google.gcloud.compute.AddressInfo;
import com.google.gcloud.compute.Compute;
import com.google.gcloud.compute.DeprecationStatus;
import com.google.gcloud.compute.Disk;
import com.google.gcloud.compute.DiskConfiguration;
import com.google.gcloud.compute.DiskId;
import com.google.gcloud.compute.DiskImageConfiguration;
import com.google.gcloud.compute.DiskInfo;
import com.google.gcloud.compute.DiskType;
import com.google.gcloud.compute.DiskTypeId;
import com.google.gcloud.compute.GlobalAddressId;
import com.google.gcloud.compute.Image;
import com.google.gcloud.compute.ImageConfiguration;
import com.google.gcloud.compute.ImageDiskConfiguration;
import com.google.gcloud.compute.ImageId;
import com.google.gcloud.compute.ImageInfo;
import com.google.gcloud.compute.License;
import com.google.gcloud.compute.LicenseId;
import com.google.gcloud.compute.MachineType;
import com.google.gcloud.compute.Operation;
import com.google.gcloud.compute.Region;
import com.google.gcloud.compute.RegionAddressId;
import com.google.gcloud.compute.RegionOperationId;
import com.google.gcloud.compute.Snapshot;
import com.google.gcloud.compute.SnapshotDiskConfiguration;
import com.google.gcloud.compute.SnapshotId;
import com.google.gcloud.compute.SnapshotInfo;
import com.google.gcloud.compute.StandardDiskConfiguration;
import com.google.gcloud.compute.StorageImageConfiguration;
import com.google.gcloud.compute.Zone;
import com.google.gcloud.compute.ZoneOperationId;
import com.google.gcloud.compute.testing.RemoteComputeHelper;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.Iterator;
import java.util.Set;

public class ITComputeTest {

  private static final String REGION = "us-central1";
  private static final String ZONE = "us-central1-a";
  private static final String DISK_TYPE = "local-ssd";
  private static final String MACHINE_TYPE = "f1-micro";
  private static final LicenseId LICENSE_ID = LicenseId.of("ubuntu-os-cloud", "ubuntu-1404-trusty");
  private static final String BASE_RESOURCE_NAME = RemoteComputeHelper.baseResourceName();
  private static final ImageId IMAGE_ID = ImageId.of("debian-cloud", "debian-8-jessie-v20160219");
  private static final String IMAGE_PROJECT = "debian-cloud";

  private static Compute compute;

  @Rule
  public Timeout globalTimeout = Timeout.seconds(300);

  @BeforeClass
  public static void beforeClass() {
    RemoteComputeHelper computeHelper = RemoteComputeHelper.create();
    compute = computeHelper.options().service();
  }

  @Test
  public void testGetDiskType() {
    DiskType diskType = compute.getDiskType(ZONE, DISK_TYPE);
    // assertNotNull(diskType.id());
    assertEquals(ZONE, diskType.diskTypeId().zone());
    assertEquals(DISK_TYPE, diskType.diskTypeId().diskType());
    assertNotNull(diskType.creationTimestamp());
    assertNotNull(diskType.description());
    assertNotNull(diskType.validDiskSize());
    assertNotNull(diskType.defaultDiskSizeGb());
  }

  @Test
  public void testGetDiskTypeWithSelectedFields() {
    DiskType diskType = compute.getDiskType(ZONE, DISK_TYPE,
        Compute.DiskTypeOption.fields(Compute.DiskTypeField.CREATION_TIMESTAMP));
    // assertNotNull(diskType.id());
    assertEquals(ZONE, diskType.diskTypeId().zone());
    assertEquals(DISK_TYPE, diskType.diskTypeId().diskType());
    assertNotNull(diskType.creationTimestamp());
    assertNull(diskType.description());
    assertNull(diskType.validDiskSize());
    assertNull(diskType.defaultDiskSizeGb());
  }

  @Test
  public void testListDiskTypes() {
    Page<DiskType> diskPage = compute.listDiskTypes(ZONE);
    Iterator<DiskType> diskTypeIterator = diskPage.iterateAll();
    assertTrue(diskTypeIterator.hasNext());
    while (diskTypeIterator.hasNext()) {
      DiskType diskType = diskTypeIterator.next();
      // assertNotNull(diskType.id());
      assertNotNull(diskType.diskTypeId());
      assertEquals(ZONE, diskType.diskTypeId().zone());
      assertNotNull(diskType.creationTimestamp());
      assertNotNull(diskType.description());
      assertNotNull(diskType.validDiskSize());
      assertNotNull(diskType.defaultDiskSizeGb());
    }
  }

  @Test
  public void testListDiskTypesWithSelectedFields() {
    Page<DiskType> diskPage = compute.listDiskTypes(ZONE,
        Compute.DiskTypeListOption.fields(Compute.DiskTypeField.CREATION_TIMESTAMP));
    Iterator<DiskType> diskTypeIterator = diskPage.iterateAll();
    assertTrue(diskTypeIterator.hasNext());
    while (diskTypeIterator.hasNext()) {
      DiskType diskType = diskTypeIterator.next();
      assertNull(diskType.id());
      assertNotNull(diskType.diskTypeId());
      assertEquals(ZONE, diskType.diskTypeId().zone());
      assertNotNull(diskType.creationTimestamp());
      assertNull(diskType.description());
      assertNull(diskType.validDiskSize());
      assertNull(diskType.defaultDiskSizeGb());
    }
  }

  @Test
  public void testListDiskTypesWithFilter() {
    Page<DiskType> diskPage = compute.listDiskTypes(ZONE, Compute.DiskTypeListOption.filter(
        Compute.DiskTypeFilter.equals(Compute.DiskTypeField.DEFAULT_DISK_SIZE_GB, 375)));
    Iterator<DiskType> diskTypeIterator = diskPage.iterateAll();
    assertTrue(diskTypeIterator.hasNext());
    while (diskTypeIterator.hasNext()) {
      DiskType diskType = diskTypeIterator.next();
      // todo(mziccard): uncomment or remove once #695 is closed
      // assertNotNull(diskType.id());
      assertNotNull(diskType.diskTypeId());
      assertEquals(ZONE, diskType.diskTypeId().zone());
      assertNotNull(diskType.creationTimestamp());
      assertNotNull(diskType.description());
      assertNotNull(diskType.validDiskSize());
      assertEquals(375, (long) diskType.defaultDiskSizeGb());
    }
  }

  @Test
  public void testAggregatedListDiskTypes() {
    Page<DiskType> diskPage = compute.listDiskTypes();
    Iterator<DiskType> diskTypeIterator = diskPage.iterateAll();
    assertTrue(diskTypeIterator.hasNext());
    while (diskTypeIterator.hasNext()) {
      DiskType diskType = diskTypeIterator.next();
      // assertNotNull(diskType.id());
      assertNotNull(diskType.diskTypeId());
      assertNotNull(diskType.creationTimestamp());
      assertNotNull(diskType.description());
      assertNotNull(diskType.validDiskSize());
      assertNotNull(diskType.defaultDiskSizeGb());
    }
  }

  @Test
  public void testAggregatedListDiskTypesWithFilter() {
    Page<DiskType> diskPage = compute.listDiskTypes(Compute.DiskTypeAggregatedListOption.filter(
        Compute.DiskTypeFilter.notEquals(Compute.DiskTypeField.DEFAULT_DISK_SIZE_GB, 375)));
    Iterator<DiskType> diskTypeIterator = diskPage.iterateAll();
    assertTrue(diskTypeIterator.hasNext());
    while (diskTypeIterator.hasNext()) {
      DiskType diskType = diskTypeIterator.next();
      // todo(mziccard): uncomment or remove once #695 is closed
      // assertNotNull(diskType.id());
      assertNotNull(diskType.diskTypeId());
      assertNotNull(diskType.creationTimestamp());
      assertNotNull(diskType.description());
      assertNotNull(diskType.validDiskSize());
      assertNotEquals(375, (long) diskType.defaultDiskSizeGb());
    }
  }

  @Test
  public void testGetMachineType() {
    MachineType machineType = compute.getMachineType(ZONE, MACHINE_TYPE);
    assertEquals(ZONE, machineType.machineTypeId().zone());
    assertEquals(MACHINE_TYPE, machineType.machineTypeId().machineType());
    assertNotNull(machineType.id());
    assertNotNull(machineType.creationTimestamp());
    assertNotNull(machineType.description());
    assertNotNull(machineType.cpus());
    assertNotNull(machineType.memoryMb());
    assertNotNull(machineType.maximumPersistentDisks());
    assertNotNull(machineType.maximumPersistentDisksSizeGb());
  }

  @Test
  public void testGetMachineTypeWithSelectedFields() {
    MachineType machineType = compute.getMachineType(ZONE, MACHINE_TYPE,
        Compute.MachineTypeOption.fields(Compute.MachineTypeField.ID));
    assertEquals(ZONE, machineType.machineTypeId().zone());
    assertEquals(MACHINE_TYPE, machineType.machineTypeId().machineType());
    assertNotNull(machineType.id());
    assertNull(machineType.creationTimestamp());
    assertNull(machineType.description());
    assertNull(machineType.cpus());
    assertNull(machineType.memoryMb());
    assertNull(machineType.maximumPersistentDisks());
    assertNull(machineType.maximumPersistentDisksSizeGb());
  }

  @Test
  public void testListMachineTypes() {
    Page<MachineType> machinePage = compute.listMachineTypes(ZONE);
    Iterator<MachineType> machineTypeIterator = machinePage.iterateAll();
    assertTrue(machineTypeIterator.hasNext());
    while (machineTypeIterator.hasNext()) {
      MachineType machineType = machineTypeIterator.next();
      assertNotNull(machineType.machineTypeId());
      assertEquals(ZONE, machineType.machineTypeId().zone());
      assertNotNull(machineType.id());
      assertNotNull(machineType.creationTimestamp());
      assertNotNull(machineType.description());
      assertNotNull(machineType.cpus());
      assertNotNull(machineType.memoryMb());
      assertNotNull(machineType.maximumPersistentDisks());
      assertNotNull(machineType.maximumPersistentDisksSizeGb());
    }
  }

  @Test
  public void testListMachineTypesWithSelectedFields() {
    Page<MachineType> machinePage = compute.listMachineTypes(ZONE,
        Compute.MachineTypeListOption.fields(Compute.MachineTypeField.CREATION_TIMESTAMP));
    Iterator<MachineType> machineTypeIterator = machinePage.iterateAll();
    assertTrue(machineTypeIterator.hasNext());
    while (machineTypeIterator.hasNext()) {
      MachineType machineType = machineTypeIterator.next();
      assertNotNull(machineType.machineTypeId());
      assertEquals(ZONE, machineType.machineTypeId().zone());
      assertNull(machineType.id());
      assertNotNull(machineType.creationTimestamp());
      assertNull(machineType.description());
      assertNull(machineType.cpus());
      assertNull(machineType.memoryMb());
      assertNull(machineType.maximumPersistentDisks());
      assertNull(machineType.maximumPersistentDisksSizeGb());
    }
  }

  @Test
  public void testListMachineTypesWithFilter() {
    Page<MachineType> machinePage = compute.listMachineTypes(ZONE,
        Compute.MachineTypeListOption.filter(
            Compute.MachineTypeFilter.equals(Compute.MachineTypeField.GUEST_CPUS, 2)));
    Iterator<MachineType> machineTypeIterator = machinePage.iterateAll();
    assertTrue(machineTypeIterator.hasNext());
    while (machineTypeIterator.hasNext()) {
      MachineType machineType = machineTypeIterator.next();
      assertNotNull(machineType.machineTypeId());
      assertEquals(ZONE, machineType.machineTypeId().zone());
      assertNotNull(machineType.id());
      assertNotNull(machineType.creationTimestamp());
      assertNotNull(machineType.description());
      assertNotNull(machineType.cpus());
      assertEquals(2, (long) machineType.cpus());
      assertNotNull(machineType.memoryMb());
      assertNotNull(machineType.maximumPersistentDisks());
      assertNotNull(machineType.maximumPersistentDisksSizeGb());
    }
  }

  @Test
  public void testAggregatedListMachineTypes() {
    Page<MachineType> machinePage = compute.listMachineTypes();
    Iterator<MachineType> machineTypeIterator = machinePage.iterateAll();
    assertTrue(machineTypeIterator.hasNext());
    while (machineTypeIterator.hasNext()) {
      MachineType machineType = machineTypeIterator.next();
      assertNotNull(machineType.machineTypeId());
      assertNotNull(machineType.id());
      assertNotNull(machineType.creationTimestamp());
      assertNotNull(machineType.description());
      assertNotNull(machineType.cpus());
      assertNotNull(machineType.memoryMb());
      assertNotNull(machineType.maximumPersistentDisks());
      assertNotNull(machineType.maximumPersistentDisksSizeGb());
    }
  }

  @Test
  public void testAggregatedListMachineTypesWithFilter() {
    Page<MachineType> machinePage =
        compute.listMachineTypes(Compute.MachineTypeAggregatedListOption.filter(
            Compute.MachineTypeFilter.notEquals(Compute.MachineTypeField.GUEST_CPUS, 2)));
    Iterator<MachineType> machineTypeIterator = machinePage.iterateAll();
    assertTrue(machineTypeIterator.hasNext());
    while (machineTypeIterator.hasNext()) {
      MachineType machineType = machineTypeIterator.next();
      assertNotNull(machineType.machineTypeId());
      assertNotNull(machineType.id());
      assertNotNull(machineType.creationTimestamp());
      assertNotNull(machineType.description());
      assertNotNull(machineType.cpus());
      assertNotEquals(2, (long) machineType.cpus());
      assertNotNull(machineType.memoryMb());
      assertNotNull(machineType.maximumPersistentDisks());
      assertNotNull(machineType.maximumPersistentDisksSizeGb());
    }
  }

  @Test
  public void testGetLicense() {
    License license = compute.getLicense(LICENSE_ID);
    assertEquals(LICENSE_ID, license.licenseId());
    assertNotNull(license.chargesUseFee());
  }

  @Test
  public void testGetLicenseWithSelectedFields() {
    License license = compute.getLicense(LICENSE_ID, Compute.LicenseOption.fields());
    assertEquals(LICENSE_ID, license.licenseId());
    assertNull(license.chargesUseFee());
  }

  @Test
  public void testGetRegion() {
    Region region = compute.getRegion(REGION);
    assertEquals(REGION, region.regionId().region());
    assertNotNull(region.description());
    assertNotNull(region.creationTimestamp());
    assertNotNull(region.id());
    assertNotNull(region.quotas());
    assertNotNull(region.status());
    assertNotNull(region.zones());
  }

  @Test
  public void testGetRegionWithSelectedFields() {
    Region region = compute.getRegion(REGION, Compute.RegionOption.fields(Compute.RegionField.ID));
    assertEquals(REGION, region.regionId().region());
    assertNotNull(region.id());
    assertNull(region.description());
    assertNull(region.creationTimestamp());
    assertNull(region.quotas());
    assertNull(region.status());
    assertNull(region.zones());
  }

  @Test
  public void testListRegions() {
    Page<Region> regionPage = compute.listRegions();
    Iterator<Region> regionIterator = regionPage.iterateAll();
    while (regionIterator.hasNext()) {
      Region region = regionIterator.next();
      assertNotNull(region.regionId());
      assertNotNull(region.description());
      assertNotNull(region.creationTimestamp());
      assertNotNull(region.id());
      assertNotNull(region.quotas());
      assertNotNull(region.status());
      assertNotNull(region.zones());
    }
  }

  @Test
  public void testListRegionsWithSelectedFields() {
    Page<Region> regionPage =
        compute.listRegions(Compute.RegionListOption.fields(Compute.RegionField.ID));
    Iterator<Region> regionIterator = regionPage.iterateAll();
    while (regionIterator.hasNext()) {
      Region region = regionIterator.next();
      assertNotNull(region.regionId());
      assertNull(region.description());
      assertNull(region.creationTimestamp());
      assertNotNull(region.id());
      assertNull(region.quotas());
      assertNull(region.status());
      assertNull(region.zones());
    }
  }

  @Test
  public void testListRegionsWithFilter() {
    Page<Region> regionPage = compute.listRegions(Compute.RegionListOption.filter(
        Compute.RegionFilter.equals(Compute.RegionField.NAME, REGION)));
    Iterator<Region> regionIterator = regionPage.iterateAll();
    assertEquals(REGION, regionIterator.next().regionId().region());
    assertFalse(regionIterator.hasNext());
  }

  @Test
  public void testGetZone() {
    Zone zone = compute.getZone(ZONE);
    assertEquals(ZONE, zone.zoneId().zone());
    assertNotNull(zone.id());
    assertNotNull(zone.creationTimestamp());
    assertNotNull(zone.description());
    assertNotNull(zone.status());
    assertNotNull(zone.region());
  }

  @Test
  public void testGetZoneWithSelectedFields() {
    Zone zone = compute.getZone(ZONE, Compute.ZoneOption.fields(Compute.ZoneField.ID));
    assertEquals(ZONE, zone.zoneId().zone());
    assertNotNull(zone.id());
    assertNull(zone.creationTimestamp());
    assertNull(zone.description());
    assertNull(zone.status());
    assertNull(zone.region());
  }

  @Test
  public void testListZones() {
    Page<Zone> zonePage = compute.listZones();
    Iterator<Zone> zoneIterator = zonePage.iterateAll();
    while (zoneIterator.hasNext()) {
      Zone zone = zoneIterator.next();
      assertNotNull(zone.zoneId());
      assertNotNull(zone.id());
      assertNotNull(zone.creationTimestamp());
      assertNotNull(zone.description());
      assertNotNull(zone.status());
      assertNotNull(zone.region());
    }
  }

  @Test
  public void testListZonesWithSelectedFields() {
    Page<Zone> zonePage = compute.listZones(
        Compute.ZoneListOption.fields(Compute.ZoneField.CREATION_TIMESTAMP));
    Iterator<Zone> zoneIterator = zonePage.iterateAll();
    while (zoneIterator.hasNext()) {
      Zone zone = zoneIterator.next();
      assertNotNull(zone.zoneId());
      assertNull(zone.id());
      assertNotNull(zone.creationTimestamp());
      assertNull(zone.description());
      assertNull(zone.status());
      assertNull(zone.region());
    }
  }

  @Test
  public void testListZonesWithFilter() {
    Page<Zone> zonePage = compute.listZones(
        Compute.ZoneListOption.filter(Compute.ZoneFilter.equals(Compute.ZoneField.NAME, ZONE)));
    Iterator<Zone> zoneIterator = zonePage.iterateAll();
    assertEquals(ZONE, zoneIterator.next().zoneId().zone());
    assertFalse(zoneIterator.hasNext());
  }

  @Test
  public void testListGlobalOperations() {
    Page<Operation> operationPage = compute.listGlobalOperations();
    Iterator<Operation> operationIterator = operationPage.iterateAll();
    while (operationIterator.hasNext()) {
      Operation operation = operationIterator.next();
      assertNotNull(operation.id());
      assertNotNull(operation.operationId());
      // todo(mziccard): uncomment or remove once #727 is closed
      // assertNotNull(operation.creationTimestamp());
      assertNotNull(operation.operationType());
      assertNotNull(operation.status());
      assertNotNull(operation.user());
    }
  }

  @Test
  public void testListGlobalOperationsWithSelectedFields() {
    Page<Operation> operationPage =
        compute.listGlobalOperations(Compute.OperationListOption.fields(Compute.OperationField.ID));
    Iterator<Operation> operationIterator = operationPage.iterateAll();
    while (operationIterator.hasNext()) {
      Operation operation = operationIterator.next();
      assertNotNull(operation.id());
      assertNotNull(operation.operationId());
      assertNull(operation.operationType());
      assertNull(operation.targetLink());
      assertNull(operation.targetId());
      assertNull(operation.operationType());
      assertNull(operation.status());
      assertNull(operation.statusMessage());
      assertNull(operation.user());
      assertNull(operation.progress());
      assertNull(operation.description());
      assertNull(operation.insertTime());
      assertNull(operation.startTime());
      assertNull(operation.endTime());
      assertNull(operation.warnings());
      assertNull(operation.httpErrorMessage());
    }
  }

  @Test
  public void testListGlobalOperationsWithFilter() {
    Page<Operation> operationPage = compute.listGlobalOperations(Compute.OperationListOption.filter(
        Compute.OperationFilter.equals(Compute.OperationField.STATUS, "DONE")));
    Iterator<Operation> operationIterator = operationPage.iterateAll();
    while (operationIterator.hasNext()) {
      Operation operation = operationIterator.next();
      assertNotNull(operation.id());
      assertNotNull(operation.operationId());
      // todo(mziccard): uncomment or remove once #727 is closed
      // assertNotNull(operation.creationTimestamp());
      assertNotNull(operation.operationType());
      assertEquals(Operation.Status.DONE, operation.status());
      assertNotNull(operation.user());
    }
  }

  @Test
  public void testListRegionOperations() {
    Page<Operation> operationPage = compute.listRegionOperations(REGION);
    Iterator<Operation> operationIterator = operationPage.iterateAll();
    while (operationIterator.hasNext()) {
      Operation operation = operationIterator.next();
      assertNotNull(operation.id());
      assertNotNull(operation.operationId());
      assertEquals(REGION, operation.<RegionOperationId>operationId().region());
      // todo(mziccard): uncomment or remove once #727 is closed
      // assertNotNull(operation.creationTimestamp());
      assertNotNull(operation.operationType());
      assertNotNull(operation.status());
      assertNotNull(operation.user());
    }
  }

  @Test
  public void testListRegionOperationsWithSelectedFields() {
    Page<Operation> operationPage = compute.listRegionOperations(REGION,
        Compute.OperationListOption.fields(Compute.OperationField.ID));
    Iterator<Operation> operationIterator = operationPage.iterateAll();
    while (operationIterator.hasNext()) {
      Operation operation = operationIterator.next();
      assertNotNull(operation.id());
      assertNotNull(operation.operationId());
      assertEquals(REGION, operation.<RegionOperationId>operationId().region());
      assertNull(operation.operationType());
      assertNull(operation.targetLink());
      assertNull(operation.targetId());
      assertNull(operation.operationType());
      assertNull(operation.status());
      assertNull(operation.statusMessage());
      assertNull(operation.user());
      assertNull(operation.progress());
      assertNull(operation.description());
      assertNull(operation.insertTime());
      assertNull(operation.startTime());
      assertNull(operation.endTime());
      assertNull(operation.warnings());
      assertNull(operation.httpErrorMessage());
    }
  }

  @Test
  public void testListRegionOperationsWithFilter() {
    Page<Operation> operationPage = compute.listRegionOperations(REGION,
        Compute.OperationListOption.filter(Compute.OperationFilter.equals(
            Compute.OperationField.STATUS, "DONE")));
    Iterator<Operation> operationIterator = operationPage.iterateAll();
    while (operationIterator.hasNext()) {
      Operation operation = operationIterator.next();
      assertNotNull(operation.id());
      assertNotNull(operation.operationId());
      assertEquals(REGION, operation.<RegionOperationId>operationId().region());
      // todo(mziccard): uncomment or remove once #727 is closed
      // assertNotNull(operation.creationTimestamp());
      assertNotNull(operation.operationType());
      assertEquals(Operation.Status.DONE, operation.status());
      assertNotNull(operation.user());
    }
  }

  @Test
  public void testListZoneOperations() {
    Page<Operation> operationPage = compute.listZoneOperations(ZONE);
    Iterator<Operation> operationIterator = operationPage.iterateAll();
    while (operationIterator.hasNext()) {
      Operation operation = operationIterator.next();
      assertNotNull(operation.id());
      assertNotNull(operation.operationId());
      assertEquals(ZONE, operation.<ZoneOperationId>operationId().zone());
      // todo(mziccard): uncomment or remove once #727 is closed
      // assertNotNull(operation.creationTimestamp());
      assertNotNull(operation.operationType());
      assertNotNull(operation.status());
      assertNotNull(operation.user());
    }
  }

  @Test
  public void testListZoneOperationsWithSelectedFields() {
    Page<Operation> operationPage = compute.listZoneOperations(ZONE,
        Compute.OperationListOption.fields(Compute.OperationField.ID));
    Iterator<Operation> operationIterator = operationPage.iterateAll();
    while (operationIterator.hasNext()) {
      Operation operation = operationIterator.next();
      assertNotNull(operation.id());
      assertNotNull(operation.operationId());
      assertEquals(ZONE, operation.<ZoneOperationId>operationId().zone());
      assertNull(operation.operationType());
      assertNull(operation.targetLink());
      assertNull(operation.targetId());
      assertNull(operation.operationType());
      assertNull(operation.status());
      assertNull(operation.statusMessage());
      assertNull(operation.user());
      assertNull(operation.progress());
      assertNull(operation.description());
      assertNull(operation.insertTime());
      assertNull(operation.startTime());
      assertNull(operation.endTime());
      assertNull(operation.warnings());
      assertNull(operation.httpErrorMessage());
    }
  }

  @Test
  public void testListZoneOperationsWithFilter() {
    Page<Operation> operationPage = compute.listZoneOperations(ZONE,
        Compute.OperationListOption.filter(Compute.OperationFilter.equals(
            Compute.OperationField.STATUS, "DONE")));
    Iterator<Operation> operationIterator = operationPage.iterateAll();
    while (operationIterator.hasNext()) {
      Operation operation = operationIterator.next();
      assertNotNull(operation.id());
      assertNotNull(operation.operationId());
      assertEquals(ZONE, operation.<ZoneOperationId>operationId().zone());
      // todo(mziccard): uncomment or remove once #727 is closed
      // assertNotNull(operation.creationTimestamp());
      assertNotNull(operation.operationType());
      assertEquals(Operation.Status.DONE, operation.status());
      assertNotNull(operation.user());
    }
  }

  @Test
  public void testCreateGetAndDeleteRegionAddress() throws InterruptedException {
    String name = BASE_RESOURCE_NAME + "create-and-get-region-address";
    AddressId addressId = RegionAddressId.of(REGION, name);
    AddressInfo addressInfo = AddressInfo.of(addressId);
    Operation operation = compute.create(addressInfo);
    while (!operation.isDone()) {
      Thread.sleep(1000L);
    }
    // test get
    Address remoteAddress = compute.get(addressId);
    assertNotNull(remoteAddress);
    assertTrue(remoteAddress.addressId() instanceof RegionAddressId);
    assertEquals(REGION, remoteAddress.<RegionAddressId>addressId().region());
    assertEquals(addressId.address(), remoteAddress.addressId().address());
    assertNotNull(remoteAddress.address());
    assertNotNull(remoteAddress.creationTimestamp());
    assertNotNull(remoteAddress.id());
    assertNotNull(remoteAddress.status());
    // test get with selected fields
    remoteAddress = compute.get(addressId, Compute.AddressOption.fields());
    assertNotNull(remoteAddress);
    assertTrue(remoteAddress.addressId() instanceof RegionAddressId);
    assertEquals(REGION, remoteAddress.<RegionAddressId>addressId().region());
    assertEquals(addressId.address(), remoteAddress.addressId().address());
    assertNull(remoteAddress.address());
    assertNull(remoteAddress.creationTimestamp());
    assertNull(remoteAddress.id());
    operation = remoteAddress.delete();
    while (!operation.isDone()) {
      Thread.sleep(1000L);
    }
    assertNull(compute.get(addressId));
  }

  @Test
  public void testListRegionAddresses() throws InterruptedException {
    String prefix = BASE_RESOURCE_NAME + "list-region-address";
    String[] addressNames = {prefix + "1", prefix + "2"};
    AddressId firstAddressId = RegionAddressId.of(REGION, addressNames[0]);
    AddressId secondAddressId = RegionAddressId.of(REGION, addressNames[1]);
    Operation firstOperation = compute.create(AddressInfo.of(firstAddressId));
    Operation secondOperation = compute.create(AddressInfo.of(secondAddressId));
    while (!firstOperation.isDone()) {
      Thread.sleep(1000L);
    }
    while (!secondOperation.isDone()) {
      Thread.sleep(1000L);
    }
    Set<String> addressSet = ImmutableSet.copyOf(addressNames);
    // test list
    Compute.AddressFilter filter =
        Compute.AddressFilter.equals(Compute.AddressField.NAME, prefix + "\\d");
    Page<Address> addressPage =
        compute.listRegionAddresses(REGION, Compute.AddressListOption.filter(filter));
    Iterator<Address> addressIterator = addressPage.iterateAll();
    int count = 0;
    while (addressIterator.hasNext()) {
      Address address = addressIterator.next();
      assertNotNull(address.addressId());
      assertTrue(address.addressId() instanceof RegionAddressId);
      assertEquals(REGION, address.<RegionAddressId>addressId().region());
      assertTrue(addressSet.contains(address.addressId().address()));
      assertNotNull(address.address());
      assertNotNull(address.creationTimestamp());
      assertNotNull(address.id());
      count++;
    }
    assertEquals(2, count);
    // test list with selected fields
    count = 0;
    addressPage = compute.listRegionAddresses(REGION, Compute.AddressListOption.filter(filter),
        Compute.AddressListOption.fields(Compute.AddressField.ADDRESS));
    addressIterator = addressPage.iterateAll();
    while (addressIterator.hasNext()) {
      Address address = addressIterator.next();
      assertTrue(address.addressId() instanceof RegionAddressId);
      assertEquals(REGION, address.<RegionAddressId>addressId().region());
      assertTrue(addressSet.contains(address.addressId().address()));
      assertNotNull(address.address());
      assertNull(address.creationTimestamp());
      assertNull(address.id());
      assertNull(address.status());
      assertNull(address.usage());
      count++;
    }
    assertEquals(2, count);
    compute.delete(firstAddressId);
    compute.delete(secondAddressId);
  }

  @Test
  public void testAggregatedListAddresses() throws InterruptedException {
    String prefix = BASE_RESOURCE_NAME + "aggregated-list-address";
    String[] addressNames = {prefix + "1", prefix + "2"};
    AddressId firstAddressId = RegionAddressId.of(REGION, addressNames[0]);
    AddressId secondAddressId = GlobalAddressId.of(REGION, addressNames[1]);
    Operation firstOperation = compute.create(AddressInfo.of(firstAddressId));
    Operation secondOperation = compute.create(AddressInfo.of(secondAddressId));
    while (!firstOperation.isDone()) {
      Thread.sleep(1000L);
    }
    while (!secondOperation.isDone()) {
      Thread.sleep(1000L);
    }
    Set<String> addressSet = ImmutableSet.copyOf(addressNames);
    Compute.AddressFilter filter =
        Compute.AddressFilter.equals(Compute.AddressField.NAME, prefix + "\\d");
    Page<Address> addressPage =
        compute.listAddresses(Compute.AddressAggregatedListOption.filter(filter));
    Iterator<Address> addressIterator = addressPage.iterateAll();
    int count = 0;
    while (addressIterator.hasNext()) {
      Address address = addressIterator.next();
      assertNotNull(address.addressId());
      assertTrue(addressSet.contains(address.addressId().address()));
      assertNotNull(address.address());
      assertNotNull(address.creationTimestamp());
      assertNotNull(address.id());
      count++;
    }
    assertEquals(2, count);
    compute.delete(firstAddressId);
    compute.delete(secondAddressId);
  }

  @Test
  public void testCreateGetAndDeleteGlobalAddress() throws InterruptedException {
    String name = BASE_RESOURCE_NAME + "create-and-get-global-address";
    AddressId addressId = GlobalAddressId.of(name);
    AddressInfo addressInfo = AddressInfo.of(addressId);
    Operation operation = compute.create(addressInfo);
    while (!operation.isDone()) {
      Thread.sleep(1000L);
    }
    // test get
    Address remoteAddress = compute.get(addressId);
    assertNotNull(remoteAddress);
    assertTrue(remoteAddress.addressId() instanceof GlobalAddressId);
    assertEquals(addressId.address(), remoteAddress.addressId().address());
    assertNotNull(remoteAddress.address());
    assertNotNull(remoteAddress.creationTimestamp());
    assertNotNull(remoteAddress.id());
    assertNotNull(remoteAddress.status());
    // test get with selected fields
    remoteAddress = compute.get(addressId, Compute.AddressOption.fields());
    assertNotNull(remoteAddress);
    assertTrue(remoteAddress.addressId() instanceof GlobalAddressId);
    assertEquals(addressId.address(), remoteAddress.addressId().address());
    assertNull(remoteAddress.address());
    assertNull(remoteAddress.creationTimestamp());
    assertNull(remoteAddress.id());
    operation = remoteAddress.delete();
    while (!operation.isDone()) {
      Thread.sleep(1000L);
    }
    assertNull(compute.get(addressId));
  }

  @Test
  public void testListGlobalAddresses() throws InterruptedException {
    String prefix = BASE_RESOURCE_NAME + "list-global-address";
    String[] addressNames = {prefix + "1", prefix + "2"};
    AddressId firstAddressId = GlobalAddressId.of(addressNames[0]);
    AddressId secondAddressId = GlobalAddressId.of(addressNames[1]);
    Operation firstOperation = compute.create(AddressInfo.of(firstAddressId));
    Operation secondOperation = compute.create(AddressInfo.of(secondAddressId));
    while (!firstOperation.isDone()) {
      Thread.sleep(1000L);
    }
    while (!secondOperation.isDone()) {
      Thread.sleep(1000L);
    }
    Set<String> addressSet = ImmutableSet.copyOf(addressNames);
    // test list
    Compute.AddressFilter filter =
        Compute.AddressFilter.equals(Compute.AddressField.NAME, prefix + "\\d");
    Page<Address> addressPage =
        compute.listGlobalAddresses(Compute.AddressListOption.filter(filter));
    Iterator<Address> addressIterator = addressPage.iterateAll();
    int count = 0;
    while (addressIterator.hasNext()) {
      Address address = addressIterator.next();
      assertNotNull(address.addressId());
      assertTrue(address.addressId() instanceof GlobalAddressId);
      assertTrue(addressSet.contains(address.addressId().address()));
      assertNotNull(address.address());
      assertNotNull(address.creationTimestamp());
      assertNotNull(address.id());
      count++;
    }
    assertEquals(2, count);
    // test list with selected fields
    count = 0;
    addressPage = compute.listGlobalAddresses(Compute.AddressListOption.filter(filter),
        Compute.AddressListOption.fields(Compute.AddressField.ADDRESS));
    addressIterator = addressPage.iterateAll();
    while (addressIterator.hasNext()) {
      Address address = addressIterator.next();
      assertTrue(address.addressId() instanceof GlobalAddressId);
      assertTrue(addressSet.contains(address.addressId().address()));
      assertNotNull(address.address());
      assertNull(address.creationTimestamp());
      assertNull(address.id());
      assertNull(address.status());
      assertNull(address.usage());
      count++;
    }
    assertEquals(2, count);
    compute.delete(firstAddressId);
    compute.delete(secondAddressId);
  }

  @Test
  public void testCreateGetResizeAndDeleteStandardDisk() throws InterruptedException {
    String name = BASE_RESOURCE_NAME + "create-and-get-standard-disk";
    DiskId diskId = DiskId.of(ZONE, name);
    DiskInfo diskInfo =
        DiskInfo.of(diskId, StandardDiskConfiguration.of(DiskTypeId.of(ZONE, "pd-ssd"), 100L));
    Operation operation = compute.create(diskInfo);
    while (!operation.isDone()) {
      Thread.sleep(1000L);
    }
    // test get
    Disk remoteDisk = compute.get(diskId);
    assertNotNull(remoteDisk);
    assertEquals(ZONE, remoteDisk.diskId().zone());
    assertEquals(diskId.disk(), remoteDisk.diskId().disk());
    assertNotNull(remoteDisk.creationTimestamp());
    assertNotNull(remoteDisk.id());
    assertTrue(remoteDisk.configuration() instanceof StandardDiskConfiguration);
    StandardDiskConfiguration remoteConfiguration = remoteDisk.configuration();
    assertEquals(100L, (long) remoteConfiguration.sizeGb());
    assertEquals("pd-ssd", remoteConfiguration.diskType().diskType());
    assertEquals(DiskConfiguration.Type.STANDARD, remoteConfiguration.type());
    assertNull(remoteDisk.lastAttachTimestamp());
    assertNull(remoteDisk.lastDetachTimestamp());
    operation = remoteDisk.resize(200L);
    while (!operation.isDone()) {
      Thread.sleep(1000L);
    }
    // test resize and get with selected fields
    remoteDisk = compute.get(diskId, Compute.DiskOption.fields(Compute.DiskField.SIZE_GB));
    assertNotNull(remoteDisk);
    assertEquals(ZONE, remoteDisk.diskId().zone());
    assertEquals(diskId.disk(), remoteDisk.diskId().disk());
    assertNull(remoteDisk.creationTimestamp());
    assertNull(remoteDisk.id());
    assertTrue(remoteDisk.configuration() instanceof StandardDiskConfiguration);
    remoteConfiguration = remoteDisk.configuration();
    assertEquals(200L, (long) remoteConfiguration.sizeGb());
    assertEquals("pd-ssd", remoteConfiguration.diskType().diskType());
    assertEquals(DiskConfiguration.Type.STANDARD, remoteConfiguration.type());
    assertNull(remoteDisk.lastAttachTimestamp());
    assertNull(remoteDisk.lastDetachTimestamp());
    operation = remoteDisk.delete();
    while (!operation.isDone()) {
      Thread.sleep(1000L);
    }
    assertNull(compute.get(diskId));
  }

  @Test
  public void testCreateGetAndDeleteImageDisk() throws InterruptedException {
    String name = BASE_RESOURCE_NAME + "create-and-get-image-disk";
    DiskId diskId = DiskId.of(ZONE, name);
    DiskInfo diskInfo = DiskInfo.of(diskId, ImageDiskConfiguration.of(IMAGE_ID));
    Operation operation = compute.create(diskInfo);
    while (!operation.isDone()) {
      Thread.sleep(1000L);
    }
    // test get
    Disk remoteDisk = compute.get(diskId);
    assertNotNull(remoteDisk);
    assertEquals(ZONE, remoteDisk.diskId().zone());
    assertEquals(diskId.disk(), remoteDisk.diskId().disk());
    assertEquals(DiskInfo.CreationStatus.READY, remoteDisk.creationStatus());
    assertNotNull(remoteDisk.creationTimestamp());
    assertNotNull(remoteDisk.id());
    assertTrue(remoteDisk.configuration() instanceof ImageDiskConfiguration);
    ImageDiskConfiguration remoteConfiguration = remoteDisk.configuration();
    assertEquals(IMAGE_ID, remoteConfiguration.sourceImage());
    assertNotNull(remoteConfiguration.sourceImageId());
    assertEquals(DiskConfiguration.Type.IMAGE, remoteConfiguration.type());
    assertNotNull(remoteConfiguration.sizeGb());
    assertEquals("pd-standard", remoteConfiguration.diskType().diskType());
    assertNull(remoteDisk.lastAttachTimestamp());
    assertNull(remoteDisk.lastDetachTimestamp());
    // test get with selected fields
    remoteDisk = compute.get(diskId, Compute.DiskOption.fields());
    assertNotNull(remoteDisk);
    assertEquals(ZONE, remoteDisk.diskId().zone());
    assertEquals(diskId.disk(), remoteDisk.diskId().disk());
    assertNull(remoteDisk.creationTimestamp());
    assertNull(remoteDisk.id());
    assertTrue(remoteDisk.configuration() instanceof ImageDiskConfiguration);
    remoteConfiguration = remoteDisk.configuration();
    assertEquals(IMAGE_ID, remoteConfiguration.sourceImage());
    assertNull(remoteConfiguration.sourceImageId());
    assertEquals(DiskConfiguration.Type.IMAGE, remoteConfiguration.type());
    assertNull(remoteConfiguration.sizeGb());
    assertEquals("pd-standard", remoteConfiguration.diskType().diskType());
    assertNull(remoteDisk.lastAttachTimestamp());
    assertNull(remoteDisk.lastDetachTimestamp());
    operation = remoteDisk.delete();
    while (!operation.isDone()) {
      Thread.sleep(1000L);
    }
    assertNull(compute.get(diskId));
  }

  @Test
  public void testCreateGetAndDeleteSnapshotAndSnapshotDisk() throws InterruptedException {
    String diskName = BASE_RESOURCE_NAME + "create-and-get-snapshot-disk1";
    String snapshotDiskName = BASE_RESOURCE_NAME + "create-and-get-snapshot-disk2";
    DiskId diskId = DiskId.of(ZONE, diskName);
    DiskId snapshotDiskId = DiskId.of(ZONE, snapshotDiskName);
    String snapshotName = BASE_RESOURCE_NAME + "create-and-get-snapshot";
    DiskInfo diskInfo =
        DiskInfo.of(diskId, StandardDiskConfiguration.of(DiskTypeId.of(ZONE, "pd-ssd"), 100L));
    Operation operation = compute.create(diskInfo);
    while (!operation.isDone()) {
      Thread.sleep(1000L);
    }
    Disk remoteDisk = compute.get(diskId);
    operation = remoteDisk.createSnapshot(snapshotName);
    while (!operation.isDone()) {
      Thread.sleep(1000L);
    }
    // test get snapshot with selected fields
    Snapshot snapshot = compute.getSnapshot(snapshotName,
        Compute.SnapshotOption.fields(Compute.SnapshotField.CREATION_TIMESTAMP));
    assertNull(snapshot.id());
    assertNotNull(snapshot.snapshotId());
    assertNotNull(snapshot.creationTimestamp());
    assertNull(snapshot.description());
    assertNull(snapshot.status());
    assertNull(snapshot.diskSizeGb());
    assertNull(snapshot.licenses());
    assertNull(snapshot.sourceDisk());
    assertNull(snapshot.sourceDiskId());
    assertNull(snapshot.storageBytes());
    assertNull(snapshot.storageBytesStatus());
    // test get snapshot
    snapshot = compute.getSnapshot(snapshotName);
    assertNotNull(snapshot.id());
    assertNotNull(snapshot.snapshotId());
    assertNotNull(snapshot.creationTimestamp());
    assertNotNull(snapshot.status());
    assertEquals(100L, (long) snapshot.diskSizeGb());
    assertEquals(diskName, snapshot.sourceDisk().disk());
    assertNotNull(snapshot.sourceDiskId());
    assertNotNull(snapshot.storageBytes());
    assertNotNull(snapshot.storageBytesStatus());
    remoteDisk.delete();
    diskInfo =
        DiskInfo.of(snapshotDiskId, SnapshotDiskConfiguration.of(SnapshotId.of(snapshotName)));
    operation = compute.create(diskInfo);
    while (!operation.isDone()) {
      Thread.sleep(1000L);
    }
    // test get disk
    remoteDisk = compute.get(snapshotDiskId);
    assertNotNull(remoteDisk);
    assertEquals(ZONE, remoteDisk.diskId().zone());
    assertEquals(snapshotDiskId.disk(), remoteDisk.diskId().disk());
    assertEquals(DiskInfo.CreationStatus.READY, remoteDisk.creationStatus());
    assertNotNull(remoteDisk.creationTimestamp());
    assertNotNull(remoteDisk.id());
    assertTrue(remoteDisk.configuration() instanceof SnapshotDiskConfiguration);
    SnapshotDiskConfiguration remoteConfiguration = remoteDisk.configuration();
    assertEquals(DiskConfiguration.Type.SNAPSHOT, remoteConfiguration.type());
    assertEquals(snapshotName, remoteConfiguration.sourceSnapshot().snapshot());
    assertEquals(100L, (long) remoteConfiguration.sizeGb());
    assertEquals("pd-standard", remoteConfiguration.diskType().diskType());
    assertNotNull(remoteConfiguration.sourceSnapshotId());
    assertNull(remoteDisk.lastAttachTimestamp());
    assertNull(remoteDisk.lastDetachTimestamp());
    // test get disk with selected fields
    remoteDisk = compute.get(snapshotDiskId, Compute.DiskOption.fields());
    assertNotNull(remoteDisk);
    assertEquals(ZONE, remoteDisk.diskId().zone());
    assertEquals(snapshotDiskId.disk(), remoteDisk.diskId().disk());
    assertNull(remoteDisk.creationStatus());
    assertNull(remoteDisk.creationTimestamp());
    assertNull(remoteDisk.id());
    assertTrue(remoteDisk.configuration() instanceof SnapshotDiskConfiguration);
    remoteConfiguration = remoteDisk.configuration();
    assertEquals(DiskConfiguration.Type.SNAPSHOT, remoteConfiguration.type());
    assertEquals(snapshotName, remoteConfiguration.sourceSnapshot().snapshot());
    assertNull(remoteConfiguration.sizeGb());
    assertEquals("pd-standard", remoteConfiguration.diskType().diskType());
    assertNull(remoteDisk.<SnapshotDiskConfiguration>configuration().sourceSnapshotId());
    assertNull(remoteDisk.lastAttachTimestamp());
    assertNull(remoteDisk.lastDetachTimestamp());
    operation = remoteDisk.delete();
    while (!operation.isDone()) {
      Thread.sleep(1000L);
    }
    assertNull(compute.get(snapshotDiskId));
    operation = snapshot.delete();
    while (!operation.isDone()) {
      Thread.sleep(1000L);
    }
    assertNull(compute.getSnapshot(snapshotName));
  }

  @Test
  public void testListDisksAndSnapshots() throws InterruptedException {
    String prefix = BASE_RESOURCE_NAME + "list-disks-and-snapshots-disk";
    String[] diskNames = {prefix + "1", prefix + "2"};
    DiskId firstDiskId = DiskId.of(ZONE, diskNames[0]);
    DiskId secondDiskId = DiskId.of(ZONE, diskNames[1]);
    DiskConfiguration configuration =
        StandardDiskConfiguration.of(DiskTypeId.of(ZONE, "pd-ssd"), 100L);
    Operation firstOperation = compute.create(DiskInfo.of(firstDiskId, configuration));
    Operation secondOperation = compute.create(DiskInfo.of(secondDiskId, configuration));
    while (!firstOperation.isDone()) {
      Thread.sleep(1000L);
    }
    while (!secondOperation.isDone()) {
      Thread.sleep(1000L);
    }
    Set<String> diskSet = ImmutableSet.copyOf(diskNames);
    // test list disks
    Compute.DiskFilter diskFilter =
        Compute.DiskFilter.equals(Compute.DiskField.NAME, prefix + "\\d");
    Page<Disk> diskPage = compute.listDisks(ZONE, Compute.DiskListOption.filter(diskFilter));
    Iterator<Disk> diskIterator = diskPage.iterateAll();
    int count = 0;
    while (diskIterator.hasNext()) {
      Disk remoteDisk = diskIterator.next();
      assertEquals(ZONE, remoteDisk.diskId().zone());
      assertTrue(diskSet.contains(remoteDisk.diskId().disk()));
      assertEquals(DiskInfo.CreationStatus.READY, remoteDisk.creationStatus());
      assertNotNull(remoteDisk.creationTimestamp());
      assertNotNull(remoteDisk.id());
      assertTrue(remoteDisk.configuration() instanceof StandardDiskConfiguration);
      StandardDiskConfiguration remoteConfiguration = remoteDisk.configuration();
      assertEquals(100L, (long) remoteConfiguration.sizeGb());
      assertEquals("pd-ssd", remoteConfiguration.diskType().diskType());
      assertEquals(DiskConfiguration.Type.STANDARD, remoteConfiguration.type());
      assertNull(remoteDisk.lastAttachTimestamp());
      assertNull(remoteDisk.lastDetachTimestamp());
      count++;
    }
    assertEquals(2, count);
    // test list disks with selected fields
    count = 0;
    diskPage = compute.listDisks(ZONE, Compute.DiskListOption.filter(diskFilter),
        Compute.DiskListOption.fields(Compute.DiskField.STATUS));
    diskIterator = diskPage.iterateAll();
    while (diskIterator.hasNext()) {
      Disk remoteDisk = diskIterator.next();
      assertEquals(ZONE, remoteDisk.diskId().zone());
      assertTrue(diskSet.contains(remoteDisk.diskId().disk()));
      assertEquals(DiskInfo.CreationStatus.READY, remoteDisk.creationStatus());
      assertNull(remoteDisk.creationTimestamp());
      assertNull(remoteDisk.id());
      assertTrue(remoteDisk.configuration() instanceof StandardDiskConfiguration);
      StandardDiskConfiguration remoteConfiguration = remoteDisk.configuration();
      assertNull(remoteConfiguration.sizeGb());
      assertEquals("pd-ssd", remoteConfiguration.diskType().diskType());
      assertEquals(DiskConfiguration.Type.STANDARD, remoteConfiguration.type());
      assertNull(remoteDisk.lastAttachTimestamp());
      assertNull(remoteDisk.lastDetachTimestamp());
      count++;
    }
    assertEquals(2, count);
    // test snapshots
    SnapshotId firstSnapshotId = SnapshotId.of(diskNames[0]);
    SnapshotId secondSnapshotId = SnapshotId.of(diskNames[1]);
    firstOperation = compute.create(SnapshotInfo.of(firstSnapshotId, firstDiskId));
    secondOperation = compute.create(SnapshotInfo.of(secondSnapshotId, secondDiskId));
    while (!firstOperation.isDone()) {
      Thread.sleep(1000L);
    }
    while (!secondOperation.isDone()) {
      Thread.sleep(1000L);
    }
    // test list snapshots
    Compute.SnapshotFilter snapshotFilter =
        Compute.SnapshotFilter.equals(Compute.SnapshotField.NAME, prefix + "\\d");
    Page<Snapshot> snapshotPage =
        compute.listSnapshots(Compute.SnapshotListOption.filter(snapshotFilter));
    Iterator<Snapshot> snapshotIterator = snapshotPage.iterateAll();
    count = 0;
    while (snapshotIterator.hasNext()) {
      Snapshot remoteSnapshot = snapshotIterator.next();
      assertNotNull(remoteSnapshot.id());
      assertTrue(diskSet.contains(remoteSnapshot.snapshotId().snapshot()));
      assertNotNull(remoteSnapshot.creationTimestamp());
      assertNotNull(remoteSnapshot.status());
      assertEquals(100L, (long) remoteSnapshot.diskSizeGb());
      assertTrue(diskSet.contains(remoteSnapshot.sourceDisk().disk()));
      assertNotNull(remoteSnapshot.sourceDiskId());
      assertNotNull(remoteSnapshot.storageBytes());
      assertNotNull(remoteSnapshot.storageBytesStatus());
      count++;
    }
    assertEquals(2, count);
    // test list snapshots with selected fields
    snapshotPage = compute.listSnapshots(Compute.SnapshotListOption.filter(snapshotFilter),
        Compute.SnapshotListOption.fields(Compute.SnapshotField.CREATION_TIMESTAMP));
    snapshotIterator = snapshotPage.iterateAll();
    count = 0;
    while (snapshotIterator.hasNext()) {
      Snapshot remoteSnapshot = snapshotIterator.next();
      assertNull(remoteSnapshot.id());
      assertTrue(diskSet.contains(remoteSnapshot.snapshotId().snapshot()));
      assertNotNull(remoteSnapshot.creationTimestamp());
      assertNull(remoteSnapshot.status());
      assertNull(remoteSnapshot.diskSizeGb());
      assertNull(remoteSnapshot.sourceDisk());
      assertNull(remoteSnapshot.sourceDiskId());
      assertNull(remoteSnapshot.storageBytes());
      assertNull(remoteSnapshot.storageBytesStatus());
      count++;
    }
    assertEquals(2, count);
    compute.delete(firstDiskId);
    compute.delete(secondDiskId);
    compute.deleteSnapshot(firstSnapshotId);
    compute.deleteSnapshot(secondSnapshotId);
  }

  @Test
  public void testAggregatedListDisks() throws InterruptedException {
    String prefix = BASE_RESOURCE_NAME + "list-aggregated-disk";
    String[] diskZones = {"us-central1-a", "us-east1-c"};
    String[] diskNames = {prefix + "1", prefix + "2"};
    DiskId firstDiskId = DiskId.of(diskZones[0], diskNames[0]);
    DiskId secondDiskId = DiskId.of(diskZones[1], diskNames[1]);
    DiskConfiguration configuration =
        StandardDiskConfiguration.of(DiskTypeId.of(ZONE, "pd-ssd"), 100L);
    Operation firstOperation = compute.create(DiskInfo.of(firstDiskId, configuration));
    Operation secondOperation = compute.create(DiskInfo.of(secondDiskId, configuration));
    while (!firstOperation.isDone()) {
      Thread.sleep(1000L);
    }
    while (!secondOperation.isDone()) {
      Thread.sleep(1000L);
    }
    Set<String> zoneSet = ImmutableSet.copyOf(diskZones);
    Set<String> diskSet = ImmutableSet.copyOf(diskNames);
    Compute.DiskFilter diskFilter =
        Compute.DiskFilter.equals(Compute.DiskField.NAME, prefix + "\\d");
    Page<Disk> diskPage = compute.listDisks(Compute.DiskAggregatedListOption.filter(diskFilter));
    Iterator<Disk> diskIterator = diskPage.iterateAll();
    int count = 0;
    while (diskIterator.hasNext()) {
      Disk remoteDisk = diskIterator.next();
      assertTrue(zoneSet.contains(remoteDisk.diskId().zone()));
      assertTrue(diskSet.contains(remoteDisk.diskId().disk()));
      assertEquals(DiskInfo.CreationStatus.READY, remoteDisk.creationStatus());
      assertNotNull(remoteDisk.creationTimestamp());
      assertNotNull(remoteDisk.id());
      assertTrue(remoteDisk.configuration() instanceof StandardDiskConfiguration);
      StandardDiskConfiguration remoteConfiguration = remoteDisk.configuration();
      assertEquals(100L, (long) remoteConfiguration.sizeGb());
      assertEquals("pd-ssd", remoteConfiguration.diskType().diskType());
      assertEquals(DiskConfiguration.Type.STANDARD, remoteConfiguration.type());
      count++;
    }
    assertEquals(2, count);
    compute.delete(firstDiskId);
    compute.delete(secondDiskId);
  }

  @Test
  public void testCreateGetAndDeprecateImage() throws InterruptedException {
    String diskName = BASE_RESOURCE_NAME + "create-and-get-image-disk";
    String imageName = BASE_RESOURCE_NAME + "create-and-get-image";
    DiskId diskId = DiskId.of(ZONE, diskName);
    ImageId imageId = ImageId.of(imageName);
    DiskInfo diskInfo =
        DiskInfo.of(diskId, StandardDiskConfiguration.of(DiskTypeId.of(ZONE, "pd-ssd"), 100L));
    Operation operation = compute.create(diskInfo);
    while (!operation.isDone()) {
      Thread.sleep(1000L);
    }
    Disk remoteDisk = compute.get(diskId);
    ImageInfo imageInfo = ImageInfo.of(imageId, DiskImageConfiguration.of(diskId));
    operation = compute.create(imageInfo);
    while (!operation.isDone()) {
      Thread.sleep(1000L);
    }
    // test get image with selected fields
    Image image = compute.get(imageId,
        Compute.ImageOption.fields(Compute.ImageField.CREATION_TIMESTAMP));
    assertNull(image.id());
    assertNotNull(image.imageId());
    assertNotNull(image.creationTimestamp());
    assertNull(image.description());
    assertNotNull(image.configuration());
    assertTrue(image.configuration() instanceof DiskImageConfiguration);
    DiskImageConfiguration remoteConfiguration = image.configuration();
    assertEquals(ImageConfiguration.Type.DISK, remoteConfiguration.type());
    assertEquals(diskName, remoteConfiguration.sourceDisk().disk());
    assertNull(image.status());
    assertNull(image.diskSizeGb());
    assertNull(image.licenses());
    assertNull(image.deprecationStatus());
    // test get image
    image = compute.get(imageId);
    assertNotNull(image.id());
    assertNotNull(image.imageId());
    assertNotNull(image.creationTimestamp());
    assertNotNull(image.configuration());
    assertTrue(image.configuration() instanceof DiskImageConfiguration);
    remoteConfiguration = image.configuration();
    assertEquals(ImageConfiguration.Type.DISK, remoteConfiguration.type());
    assertEquals(diskName, remoteConfiguration.sourceDisk().disk());
    assertEquals(100L, (long) image.diskSizeGb());
    assertNotNull(image.status());
    assertNull(image.deprecationStatus());
    // test deprecate image
    DeprecationStatus<ImageId> deprecationStatus =
        DeprecationStatus.builder(DeprecationStatus.Status.DEPRECATED, imageId)
            .deprecated(System.currentTimeMillis())
            .build();
    operation = image.deprecate(deprecationStatus);
    while (!operation.isDone()) {
      Thread.sleep(1000L);
    }
    image = compute.get(imageId);
    assertEquals(deprecationStatus, image.deprecationStatus());
    remoteDisk.delete();
    operation = image.delete();
    while (!operation.isDone()) {
      Thread.sleep(1000L);
    }
    assertNull(compute.get(imageId));
  }

  @Test
  public void testListImages() {
    Page<Image> imagePage = compute.listImages(IMAGE_PROJECT);
    Iterator<Image> imageIterator = imagePage.iterateAll();
    int count = 0;
    while (imageIterator.hasNext()) {
      count++;
      Image image = imageIterator.next();
      assertNotNull(image.id());
      assertNotNull(image.imageId());
      assertNotNull(image.creationTimestamp());
      assertNotNull(image.configuration());
      assertNotNull(image.status());
      assertNotNull(image.diskSizeGb());
    }
    assertTrue(count > 0);
  }

  @Test
  public void testListImagesWithSelectedFields() {
    Page<Image> imagePage =
        compute.listImages(IMAGE_PROJECT, Compute.ImageListOption.fields(Compute.ImageField.ID));
    Iterator<Image> imageIterator = imagePage.iterateAll();
    int count = 0;
    while (imageIterator.hasNext()) {
      count++;
      Image image = imageIterator.next();
      assertNotNull(image.id());
      assertNotNull(image.imageId());
      assertNull(image.creationTimestamp());
      assertNotNull(image.configuration());
      assertNull(image.status());
      assertNull(image.diskSizeGb());
      assertNull(image.licenses());
      assertNull(image.deprecationStatus());
    }
    assertTrue(count > 0);
  }

  @Test
  public void testListImagesWithFilter() {
    Page<Image> imagePage = compute.listImages(IMAGE_PROJECT, Compute.ImageListOption.filter(
        Compute.ImageFilter.equals(Compute.ImageField.ARCHIVE_SIZE_BYTES, 365056004L)));
    Iterator<Image> imageIterator = imagePage.iterateAll();
    int count = 0;
    while (imageIterator.hasNext()) {
      count++;
      Image image = imageIterator.next();
      assertNotNull(image.id());
      assertNotNull(image.imageId());
      assertNotNull(image.creationTimestamp());
      assertNotNull(image.configuration());
      assertNotNull(image.status());
      assertNotNull(image.diskSizeGb());
      assertEquals(365056004L,
          (long) image.<StorageImageConfiguration>configuration().archiveSizeBytes());
    }
    assertTrue(count > 0);
  }
}