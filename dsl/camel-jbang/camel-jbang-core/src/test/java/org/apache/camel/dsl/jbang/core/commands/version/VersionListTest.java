/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.dsl.jbang.core.commands.version;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.tooling.model.ReleaseModel;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VersionListTest extends CamelCommandBaseTestSupport {

    @Test
    void springbootVersionIsAvailable() throws Exception {
        VersionList versionList = new VersionList(new CamelJBangMain().withPrinter(printer));
        versionList.sort = "version";
        versionList.runtime = RuntimeType.springBoot;

        versionList.doCall();

        List<String> lines = printer.getLines();
        // normalize multiple spaces to single space to avoid failures due to column width changes
        String output = normalizeSpaces(lines.stream().collect(Collectors.joining("\n")));
        // there was a change where the information is stored in 4.15, thus the test on 4.14.1 and 4.15.0
        Assertions.assertThat(output)
                .contains("4.14.1 3.5.6 17,21 LTS")
                .contains("4.15.0 3.5.6 17,21");
    }

    @Test
    void quarkusVersionIsAvailable() throws Exception {
        VersionList versionList = new VersionList(new CamelJBangMain().withPrinter(printer));
        versionList.sort = "version";
        versionList.runtime = RuntimeType.quarkus;

        versionList.doCall();

        List<String> lines = printer.getLines();
        // normalize multiple spaces to single space to avoid failures due to column width changes
        String output = normalizeSpaces(lines.stream().collect(Collectors.joining("\n")));
        Assertions.assertThat(output)
                .contains("4.14.0 3.27.0 17,21 LTS");
    }

    @ParameterizedTest
    @CsvSource({
            "4.0.0, 17&21, lts",
            "4.0.6, 17&21, lts",
            "4.4.1, 17&21, lts",
            "4.8.0, 17&21, lts",
            "4.10.0, 17&21, lts",
            "4.14.2, 17&21, lts",
            "4.18.0, 17&21, lts",
            "4.9.0, 17&21, ",
            "4.11.0, 17&21, ",
            "4.12.0, 17&21, ",
            "4.15.0, 17&21, ",
            "3.20.0, 11&17, ",
    })
    void deriveReleaseMetadata(String version, String expectedJdk, String expectedKind) {
        ReleaseModel rm = VersionList.deriveReleaseMetadata(version);
        Assertions.assertThat(rm).isNotNull();
        Assertions.assertThat(rm.getVersion()).isEqualTo(version);
        Assertions.assertThat(rm.getJdk()).isEqualTo(expectedJdk.replace('&', ','));
        if (expectedKind == null || expectedKind.isEmpty()) {
            Assertions.assertThat(rm.getKind()).isNull();
        } else {
            Assertions.assertThat(rm.getKind()).isEqualTo(expectedKind);
        }
        Assertions.assertThat(rm.getDate()).isNull();
        Assertions.assertThat(rm.getEol()).isNull();
    }

    @Test
    void deriveReleaseMetadataReturnsNullForInvalidVersions() {
        Assertions.assertThat(VersionList.deriveReleaseMetadata(null)).isNull();
        Assertions.assertThat(VersionList.deriveReleaseMetadata("abc")).isNull();
        Assertions.assertThat(VersionList.deriveReleaseMetadata("4")).isNull();
    }

    @Test
    void mergeReleasesOnlineOverridesEmbedded() {
        ReleaseModel embedded = new ReleaseModel();
        embedded.setVersion("4.8.0");
        embedded.setDate("2024-09-15");
        embedded.setKind("lts");
        embedded.setJdk("17,21");

        ReleaseModel online = new ReleaseModel();
        online.setVersion("4.8.0");
        online.setDate("2024-09-16");
        online.setKind("lts");
        online.setJdk("17,21");

        List<ReleaseModel> embeddedList = new ArrayList<>(List.of(embedded));
        List<ReleaseModel> onlineList = new ArrayList<>(List.of(online));

        List<ReleaseModel> merged = VersionList.mergeReleases(embeddedList, onlineList);
        Assertions.assertThat(merged).hasSize(1);
        Assertions.assertThat(merged.get(0).getDate()).isEqualTo("2024-09-16");
    }

    @Test
    void mergeReleasesAddsNewVersions() {
        ReleaseModel embedded = new ReleaseModel();
        embedded.setVersion("4.8.0");

        ReleaseModel online = new ReleaseModel();
        online.setVersion("4.8.1");

        List<ReleaseModel> embeddedList = new ArrayList<>(List.of(embedded));
        List<ReleaseModel> onlineList = new ArrayList<>(List.of(online));

        List<ReleaseModel> merged = VersionList.mergeReleases(embeddedList, onlineList);
        Assertions.assertThat(merged).hasSize(2);
        Assertions.assertThat(merged).extracting(ReleaseModel::getVersion).containsExactly("4.8.0", "4.8.1");
    }

    @Test
    void parseReleasesJsonParsesValidArray() {
        String json = """
                [
                    {"version":"4.8.0","date":"2024-09-15","eol":"2025-09-14","kind":"lts","jdk":"17,21"},
                    {"version":"4.9.0","date":"2024-12-04","jdk":"17,21"}
                ]
                """;

        List<ReleaseModel> releases = VersionList.parseReleasesJson(json);
        Assertions.assertThat(releases).hasSize(2);
        Assertions.assertThat(releases.get(0).getVersion()).isEqualTo("4.8.0");
        Assertions.assertThat(releases.get(0).getKind()).isEqualTo("lts");
        Assertions.assertThat(releases.get(0).getEol()).isEqualTo("2025-09-14");
        Assertions.assertThat(releases.get(1).getVersion()).isEqualTo("4.9.0");
        Assertions.assertThat(releases.get(1).getKind()).isNull();
        Assertions.assertThat(releases.get(1).getEol()).isNull();
    }

    @Test
    void parseReleasesJsonReturnsEmptyForInvalidJson() {
        Assertions.assertThat(VersionList.parseReleasesJson("not json")).isEmpty();
        Assertions.assertThat(VersionList.parseReleasesJson("")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "0, true",
            "1, false",
            "2, false",
            "3, false",
            "4, true",
            "5, false",
            "6, false",
            "7, false",
            "8, true",
            "9, false",
            "10, true",
            "11, false",
            "12, false",
            "13, false",
            "14, true",
            "15, false",
            "16, false",
            "17, false",
            "18, true",
            "22, true",
    })
    void isLtsMinor(int minor, boolean expected) {
        Assertions.assertThat(VersionList.isLtsMinor(minor)).isEqualTo(expected);
    }

    private static String normalizeSpaces(String input) {
        return input.replaceAll("\\s+", " ");
    }

}
