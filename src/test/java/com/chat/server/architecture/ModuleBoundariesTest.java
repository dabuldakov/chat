package com.chat.server.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Проверяет, что модули (feature-пакеты) не образуют циклов и что технические
 * модули не тянут за собой доменные. Это фиксирует архитектуру в коде: сборка
 * падает, если кто-то добавит обратную зависимость.
 */
@AnalyzeClasses(packages = "com.chat.server", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundariesTest {

    @ArchTest
    static final ArchRule module_slices_are_free_of_cycles = com.tngtech.archunit.library.dependencies.SlicesRuleDefinition
            .slices()
            .matching("com.chat.server.(*)..")
            .should()
            .beFreeOfCycles();

    @ArchTest
    static final ArchRule storage_is_independent = noClasses()
            .that().resideInAPackage("..storage..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..identity..", "..conversation..", "..contacts..", "..block..",
                    "..notification..", "..sync..", "..account..", "..config..");

    @ArchTest
    static final ArchRule identity_does_not_depend_on_feature_modules = noClasses()
            .that().resideInAPackage("..identity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..conversation..", "..contacts..", "..block..",
                    "..notification..", "..sync..", "..account..");

    @ArchTest
    static final ArchRule notification_only_depends_on_identity = noClasses()
            .that().resideInAPackage("..notification..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..conversation..", "..contacts..", "..block..",
                    "..sync..", "..account..", "..storage..");

    @ArchTest
    static final ArchRule account_is_a_leaf_orchestrator = noClasses()
            .that().resideOutsideOfPackage("..account..")
            .should().dependOnClassesThat().resideInAPackage("..account..");

    @ArchTest
    static final ArchRule contacts_do_not_depend_on_block_or_conversation = noClasses()
            .that().resideInAPackage("..contacts..")
            .should().dependOnClassesThat().resideInAnyPackage("..block..", "..conversation..");
}
