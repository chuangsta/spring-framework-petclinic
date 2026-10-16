package org.springframework.samples.petclinic.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.support.FormattingConversionServiceFactoryBean;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.Visit;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test class for {@link VisitController}
 *
 * @author Colin But
 */
@SpringJUnitWebConfig(locations = {"classpath:spring/mvc-core-config.xml", "classpath:spring/mvc-test-config.xml"})
class VisitControllerTests {

    private static final int TEST_OWNER_ID = 1;
    private static final int TEST_PET_ID = 1;

    @Autowired
    private VisitController visitController;

    @Autowired
    private FormattingConversionServiceFactoryBean formattingConversionServiceFactoryBean;

    @Autowired
    private ClinicService clinicService;

    private MockMvc mockMvc;

    private Pet leo;

    @BeforeEach
    void setup() {
        reset(this.clinicService);
        this.mockMvc = MockMvcBuilders
            .standaloneSetup(visitController)
            .setConversionService(formattingConversionServiceFactoryBean.getObject())
            .build();

        leo = new Pet();
        leo.setId(TEST_PET_ID);
        leo.setName("Leo");
        given(this.clinicService.findPetById(TEST_PET_ID)).willReturn(leo);
    }

    @Test
    void testInitNewVisitForm() throws Exception {
        mockMvc.perform(get("/owners/*/pets/{petId}/visits/new", TEST_PET_ID))
            .andExpect(status().isOk())
            .andExpect(view().name("pets/createOrUpdateVisitForm"));
    }

    @Test
    void testProcessNewVisitFormSuccess() throws Exception {
        mockMvc.perform(post("/owners/*/pets/{petId}/visits/new", TEST_PET_ID)
            .param("name", "George")
            .param("description", "Visit Description")
        )
            .andExpect(status().is3xxRedirection())
            .andExpect(view().name("redirect:/owners/{ownerId}"));
    }

    @Test
    void testProcessNewVisitFormHasErrors() throws Exception {
        mockMvc.perform(post("/owners/*/pets/{petId}/visits/new", TEST_PET_ID)
            .param("name", "George")
        )
            .andExpect(model().attributeHasErrors("visit"))
            .andExpect(status().isOk())
            .andExpect(view().name("pets/createOrUpdateVisitForm"));
    }

    @Test
    void testProcessNewVisitFormEmptyDescriptionIsRequiredAndNotSaved() throws Exception {
        mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID)
            .param("date", "2013/01/01")
            .param("description", "")
        )
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrorCode("visit", "description", "NotEmpty"))
            .andExpect(view().name("pets/createOrUpdateVisitForm"));

        verify(this.clinicService, never()).saveVisit(any(Visit.class));
    }

    @Test
    void testProcessNewVisitFormSavesBoundVisitWithDefaultDate() throws Exception {
        LocalDate before = LocalDate.now();

        mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID)
            .param("description", "Annual check-up")
        )
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/owners/" + TEST_OWNER_ID));

        LocalDate after = LocalDate.now();
        ArgumentCaptor<Visit> captor = ArgumentCaptor.forClass(Visit.class);
        verify(this.clinicService, times(1)).saveVisit(captor.capture());
        Visit saved = captor.getValue();
        assertThat(saved.getDescription()).isEqualTo("Annual check-up");
        assertThat(saved.getPet()).isSameAs(leo);
        assertThat(saved.getPet().getId()).isEqualTo(TEST_PET_ID);
        assertThat(saved.getDate()).isBetween(before, after);
        assertThat(leo.getVisits()).containsExactly(saved);
    }

    @Test
    void testProcessNewVisitFormBindsExplicitSlashDate() throws Exception {
        mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID)
            .param("date", "2013/01/04")
            .param("description", "Rabies shot")
        )
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/owners/" + TEST_OWNER_ID));

        ArgumentCaptor<Visit> captor = ArgumentCaptor.forClass(Visit.class);
        verify(this.clinicService, times(1)).saveVisit(captor.capture());
        Visit saved = captor.getValue();
        assertThat(saved.getDate()).isEqualTo(LocalDate.of(2013, 1, 4));
        assertThat(saved.getDescription()).isEqualTo("Rabies shot");
        assertThat(saved.getPet()).isSameAs(leo);
    }

    @Test
    void testShowVisits() throws Exception {
        mockMvc.perform(get("/owners/*/pets/{petId}/visits", TEST_PET_ID))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("visits"))
            .andExpect(view().name("visitList"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testShowVisitsOrdersByDateDescending() throws Exception {
        Visit oldest = visit(LocalDate.of(2011, 3, 4), "oldest");
        Visit newest = visit(LocalDate.of(2013, 1, 4), "newest");
        Visit middle = visit(LocalDate.of(2012, 6, 15), "middle");
        leo.addVisit(oldest);
        leo.addVisit(newest);
        leo.addVisit(middle);

        MvcResult result = mockMvc.perform(get("/owners/*/pets/{petId}/visits", TEST_PET_ID))
            .andExpect(status().isOk())
            .andExpect(view().name("visitList"))
            .andReturn();

        // loadPetWithVisit runs first and attaches an unsaved, today-dated visit to the same pet
        // instance, so it is listed ahead of the fixture visits.
        List<Visit> visits = (List<Visit>) result.getModelAndView().getModel().get("visits");
        assertThat(visits).hasSize(4);
        Visit transientVisit = visits.get(0);
        assertThat(transientVisit.isNew()).isTrue();
        assertThat(transientVisit.getDescription()).isNull();
        assertThat(transientVisit.getDate()).isAfterOrEqualTo(newest.getDate());
        assertThat(visits.subList(1, 4)).containsExactly(newest, middle, oldest);
    }

    private static Visit visit(LocalDate date, String description) {
        Visit visit = new Visit();
        visit.setDate(date);
        visit.setDescription(description);
        return visit;
    }

}
