package org.springframework.samples.petclinic.web;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PetValidatorTests {

    private Errors validate(Pet pet) {
        Errors errors = new BeanPropertyBindingResult(pet, "pet");
        new PetValidator().validate(pet, errors);
        return errors;
    }

    @Test
    void emptyNewPetRejectsNameTypeAndBirthDate() {
        Errors errors = validate(new Pet());

        assertThat(errors.getErrorCount()).isEqualTo(3);
        assertThat(errors.getFieldError("name").getCode()).isEqualTo("required");
        assertThat(errors.getFieldError("type").getCode()).isEqualTo("required");
        assertThat(errors.getFieldError("birthDate").getCode()).isEqualTo("required");
    }

    @Test
    void existingPetDoesNotRequireType() {
        Pet pet = new Pet();
        pet.setId(1);
        pet.setName("Max");
        pet.setBirthDate(LocalDate.of(2016, 3, 4));

        assertThat(validate(pet).hasErrors()).isFalse();
    }

    @Test
    void completeNewPetIsValid() {
        Pet pet = new Pet();
        pet.setName("Bubbles");
        pet.setType(new PetType());
        pet.setBirthDate(LocalDate.of(2015, 2, 12));

        assertThat(validate(pet).hasErrors()).isFalse();
        assertThat(new PetValidator().supports(Pet.class)).isTrue();
    }

}
