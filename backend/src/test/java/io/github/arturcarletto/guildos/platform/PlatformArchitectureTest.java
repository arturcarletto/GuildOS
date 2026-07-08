package io.github.arturcarletto.guildos.platform;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class PlatformArchitectureTest {

    @Test
    void platformAbstractionsStayNeutralOfAnyAdapter() {
        JavaClasses classes = new ClassFileImporter()
                .importPackages("io.github.arturcarletto.guildos.platform");

        noClasses()
                .that()
                .resideInAPackage("..platform..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "net.dv8tion.jda..",
                        "io.github.arturcarletto.guildos.discord..",
                        "io.github.arturcarletto.guildos.telegram..")
                .check(classes);
    }
}
