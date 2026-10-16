package org.springframework.samples.petclinic.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class VisitTests {

    @Test
    void newVisitDefaultsToToday() {
        LocalDate before = LocalDate.now();
        Visit visit = new Visit();
        LocalDate after = LocalDate.now();

        assertThat(visit.getDate()).isBetween(before, after);
    }

    @Test
    void addVisitLinksVisitToPet() {
        Pet pet = new Pet();
        pet.setId(1);
        Visit visit = new Visit();

        pet.addVisit(visit);

        assertThat(visit.getPet()).isSameAs(pet);
        assertThat(pet.getVisits()).containsExactly(visit);
    }

    @Test
    void petVisitsAreSortedByDateDescending() {
        Pet pet = new Pet();
        Visit middle = new Visit();
        middle.setDate(LocalDate.of(2012, 6, 15));
        Visit oldest = new Visit();
        oldest.setDate(LocalDate.of(2011, 3, 4));
        Visit newest = new Visit();
        newest.setDate(LocalDate.of(2013, 1, 4));
        pet.addVisit(middle);
        pet.addVisit(oldest);
        pet.addVisit(newest);

        assertThat(pet.getVisits()).containsExactly(newest, middle, oldest);
    }

}
