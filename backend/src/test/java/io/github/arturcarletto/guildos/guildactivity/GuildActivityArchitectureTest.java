package io.github.arturcarletto.guildos.guildactivity;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class GuildActivityArchitectureTest {

    @Test
    void guildActivityDoesNotDependOnJda() {
        JavaClasses classes = new ClassFileImporter()
                .importPackages("io.github.arturcarletto.guildos.guildactivity");

        noClasses()
                .that()
                .resideInAPackage("..guildactivity..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("net.dv8tion.jda..")
                .check(classes);
    }
}
