package org.springframework.samples.petclinic.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerTests {

    @Test
    void getPetMatchesNameCaseInsensitively() {
        Owner owner = new Owner();
        Pet leo = new Pet();
        leo.setId(7);
        leo.setName("Leo");
        owner.addPet(leo);

        assertThat(owner.getPet("LEO")).isSameAs(leo);
        assertThat(owner.getPet("leo", true)).isSameAs(leo);
        assertThat(owner.getPet("Max")).isNull();
    }

    @Test
    void getPetIgnoresNewPetsOnlyWhenAsked() {
        Owner owner = new Owner();
        Pet unsaved = new Pet();
        unsaved.setName("Rex");
        owner.addPet(unsaved);

        assertThat(owner.getPet("rex", true)).isNull();
        assertThat(owner.getPet("rex", false)).isSameAs(unsaved);
        assertThat(owner.getPet("rex")).isSameAs(unsaved);
    }

}
