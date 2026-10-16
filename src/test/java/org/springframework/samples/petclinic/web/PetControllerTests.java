package org.springframework.samples.petclinic.web;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.support.FormattingConversionServiceFactoryBean;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.model.Pet;
import org.springframework.samples.petclinic.model.PetType;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

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
 * Test class for the {@link PetController}
 *
 * @author Colin But
 */
@SpringJUnitWebConfig(locations = {"classpath:spring/mvc-core-config.xml", "classpath:spring/mvc-test-config.xml"})
class PetControllerTests {

    private static final int TEST_OWNER_ID = 1;
    private static final int OTHER_OWNER_ID = 2;
    private static final int TEST_PET_ID = 1;

    @Autowired
    private PetController petController;

    @Autowired
    private FormattingConversionServiceFactoryBean formattingConversionServiceFactoryBean;

    @Autowired
    private ClinicService clinicService;

    private MockMvc mockMvc;

    private PetType hamster;

    private Owner george;

    private Owner betty;

    private Pet max;

    @BeforeEach
    void setup() {
        reset(this.clinicService);
        this.mockMvc = MockMvcBuilders
            .standaloneSetup(petController)
            .setConversionService(formattingConversionServiceFactoryBean.getObject())
            .build();

        hamster = new PetType();
        hamster.setId(3);
        hamster.setName("hamster");
        given(this.clinicService.findPetTypes()).willReturn(Lists.newArrayList(hamster));

        george = new Owner();
        george.setId(TEST_OWNER_ID);
        george.setFirstName("George");
        george.setLastName("Franklin");
        Pet leo = new Pet();
        leo.setId(7);
        leo.setName("Leo");
        george.addPet(leo);
        given(this.clinicService.findOwnerById(TEST_OWNER_ID)).willReturn(george);

        betty = new Owner();
        betty.setId(OTHER_OWNER_ID);
        betty.setFirstName("Betty");
        betty.setLastName("Davis");
        given(this.clinicService.findOwnerById(OTHER_OWNER_ID)).willReturn(betty);

        max = new Pet();
        max.setId(TEST_PET_ID);
        max.setName("Max");
        given(this.clinicService.findPetById(TEST_PET_ID)).willReturn(max);
    }

    @Test
    void testInitCreationForm() throws Exception {
        mockMvc.perform(get("/owners/{ownerId}/pets/new", TEST_OWNER_ID))
            .andExpect(status().isOk())
            .andExpect(view().name("pets/createOrUpdatePetForm"))
            .andExpect(model().attributeExists("pet"));
    }

    @Test
    void testProcessCreationFormSuccess() throws Exception {
        mockMvc.perform(post("/owners/{ownerId}/pets/new", TEST_OWNER_ID)
            .param("name", "Betty")
            .param("type", "hamster")
            .param("birthDate", "2015/02/12")
        )
            .andExpect(status().is3xxRedirection())
            .andExpect(view().name("redirect:/owners/{ownerId}"));
    }

    @Test
    void testProcessCreationFormSavesBoundPetLinkedToOwner() throws Exception {
        mockMvc.perform(post("/owners/{ownerId}/pets/new", TEST_OWNER_ID)
            .param("name", "Bubbles")
            .param("type", "hamster")
            .param("birthDate", "2015/02/12")
        )
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/owners/" + TEST_OWNER_ID));

        ArgumentCaptor<Pet> captor = ArgumentCaptor.forClass(Pet.class);
        verify(this.clinicService, times(1)).savePet(captor.capture());
        Pet saved = captor.getValue();
        assertThat(saved.isNew()).isTrue();
        assertThat(saved.getName()).isEqualTo("Bubbles");
        assertThat(saved.getType()).isSameAs(hamster);
        assertThat(saved.getBirthDate()).isEqualTo(LocalDate.of(2015, 2, 12));
        assertThat(saved.getOwner()).isSameAs(george);
        assertThat(george.getPets()).contains(saved);
    }

    @Test
    void testProcessCreationFormRejectsDuplicateNameCaseInsensitive() throws Exception {
        mockMvc.perform(post("/owners/{ownerId}/pets/new", TEST_OWNER_ID)
            .param("name", "LEO")
            .param("type", "hamster")
            .param("birthDate", "2015/02/12")
        )
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrorCode("pet", "name", "duplicate"))
            .andExpect(view().name("pets/createOrUpdatePetForm"));

        verify(this.clinicService, never()).savePet(any(Pet.class));
    }

    @Test
    void testProcessCreationFormIgnoresUnsavedPetWithSameName() throws Exception {
        Pet unsaved = new Pet();
        unsaved.setName("Rex");
        george.addPet(unsaved);

        mockMvc.perform(post("/owners/{ownerId}/pets/new", TEST_OWNER_ID)
            .param("name", "rex")
            .param("type", "hamster")
            .param("birthDate", "2015/02/12")
        )
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/owners/" + TEST_OWNER_ID));

        verify(this.clinicService, times(1)).savePet(any(Pet.class));
    }

    @Test
    void testProcessCreationFormMissingName() throws Exception {
        mockMvc.perform(post("/owners/{ownerId}/pets/new", TEST_OWNER_ID)
            .param("type", "hamster")
            .param("birthDate", "2015/02/12")
        )
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrorCode("pet", "name", "required"))
            .andExpect(model().attributeErrorCount("pet", 1))
            .andExpect(view().name("pets/createOrUpdatePetForm"));

        verify(this.clinicService, never()).savePet(any(Pet.class));
    }

    @Test
    void testProcessCreationFormMissingType() throws Exception {
        mockMvc.perform(post("/owners/{ownerId}/pets/new", TEST_OWNER_ID)
            .param("name", "Bubbles")
            .param("birthDate", "2015/02/12")
        )
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrorCode("pet", "type", "required"))
            .andExpect(model().attributeErrorCount("pet", 1))
            .andExpect(view().name("pets/createOrUpdatePetForm"));

        verify(this.clinicService, never()).savePet(any(Pet.class));
    }

    @Test
    void testProcessCreationFormRejectsIsoBirthDate() throws Exception {
        mockMvc.perform(post("/owners/{ownerId}/pets/new", TEST_OWNER_ID)
            .param("name", "Bubbles")
            .param("type", "hamster")
            .param("birthDate", "2015-02-12")
        )
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrorCode("pet", "birthDate", "typeMismatch"))
            .andExpect(view().name("pets/createOrUpdatePetForm"));

        verify(this.clinicService, never()).savePet(any(Pet.class));
    }

    @Test
    void testProcessCreationFormMissingBirthDate() throws Exception {
        mockMvc.perform(post("/owners/{ownerId}/pets/new", TEST_OWNER_ID)
            .param("name", "Bubbles")
            .param("type", "hamster")
        )
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrorCode("pet", "birthDate", "required"))
            .andExpect(model().attributeErrorCount("pet", 1))
            .andExpect(view().name("pets/createOrUpdatePetForm"));

        verify(this.clinicService, never()).savePet(any(Pet.class));
    }

    @Test
    void testProcessUpdateFormWithoutIdRequiresType() throws Exception {
        mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/edit", TEST_OWNER_ID, TEST_PET_ID)
            .param("name", "Betty")
            .param("birthDate", "2015/02/12")
        )
            .andExpect(model().attributeHasNoErrors("owner"))
            .andExpect(model().attributeHasErrors("pet"))
            .andExpect(model().attributeHasFieldErrorCode("pet", "type", "required"))
            .andExpect(status().isOk())
            .andExpect(view().name("pets/createOrUpdatePetForm"));

        verify(this.clinicService, never()).savePet(any(Pet.class));
    }

    @Test
    void testInitUpdateForm() throws Exception {
        mockMvc.perform(get("/owners/{ownerId}/pets/{petId}/edit", TEST_OWNER_ID, TEST_PET_ID))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("pet"))
            .andExpect(view().name("pets/createOrUpdatePetForm"));
    }

    @Test
    void testInitUpdateFormLoadsPetById() throws Exception {
        mockMvc.perform(get("/owners/{ownerId}/pets/{petId}/edit", TEST_OWNER_ID, TEST_PET_ID))
            .andExpect(status().isOk())
            .andExpect(model().attribute("pet", max))
            .andExpect(view().name("pets/createOrUpdatePetForm"));

        verify(this.clinicService, times(1)).findPetById(TEST_PET_ID);
    }

    @Test
    void testProcessUpdateFormSuccess() throws Exception {
        mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/edit", TEST_OWNER_ID, TEST_PET_ID)
            .param("name", "Betty")
            .param("type", "hamster")
            .param("birthDate", "2015/02/12")
        )
            .andExpect(status().is3xxRedirection())
            .andExpect(view().name("redirect:/owners/{ownerId}"));
    }

    @Test
    void testProcessUpdateFormSavesBoundPetLinkedToOwner() throws Exception {
        mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/edit", TEST_OWNER_ID, TEST_PET_ID)
            .param("id", String.valueOf(TEST_PET_ID))
            .param("name", "Maximus")
            .param("type", "hamster")
            .param("birthDate", "2016/03/04")
        )
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/owners/" + TEST_OWNER_ID));

        ArgumentCaptor<Pet> captor = ArgumentCaptor.forClass(Pet.class);
        verify(this.clinicService, times(1)).savePet(captor.capture());
        Pet saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(TEST_PET_ID);
        assertThat(saved.getName()).isEqualTo("Maximus");
        assertThat(saved.getType()).isSameAs(hamster);
        assertThat(saved.getBirthDate()).isEqualTo(LocalDate.of(2016, 3, 4));
        assertThat(saved.getOwner()).isSameAs(george);
    }

    @Test
    void testProcessUpdateFormWithIdDoesNotRequireType() throws Exception {
        mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/edit", TEST_OWNER_ID, TEST_PET_ID)
            .param("id", String.valueOf(TEST_PET_ID))
            .param("name", "Maximus")
            .param("birthDate", "2016/03/04")
        )
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/owners/" + TEST_OWNER_ID));

        ArgumentCaptor<Pet> captor = ArgumentCaptor.forClass(Pet.class);
        verify(this.clinicService, times(1)).savePet(captor.capture());
        assertThat(captor.getValue().getType()).isNull();
    }

    @Test
    void testProcessUpdateFormHasErrors() throws Exception {
        mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/edit", TEST_OWNER_ID, TEST_PET_ID)
            .param("name", "Betty")
            .param("birthDate", "2015/02/12")
        )
            .andExpect(model().attributeHasNoErrors("owner"))
            .andExpect(model().attributeHasErrors("pet"))
            .andExpect(status().isOk())
            .andExpect(view().name("pets/createOrUpdatePetForm"));
    }

    /**
     * BANK RISK FLAG (cross-owner pet edit, see bank-risk-flags): the controller never checks that the
     * pet belongs to the owner in the URL. Pet 1 is George's, yet it loads and saves under Betty's path,
     * and the save re-links it to Betty. Characterized as-is; do not fix without bank authorization.
     */
    @Test
    void testCrossOwnerPetEditSucceedsAsIs_bankRiskFlag() throws Exception {
        george.addPet(max);

        mockMvc.perform(get("/owners/{ownerId}/pets/{petId}/edit", OTHER_OWNER_ID, TEST_PET_ID))
            .andExpect(status().isOk())
            .andExpect(model().attribute("pet", max))
            .andExpect(view().name("pets/createOrUpdatePetForm"));

        mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/edit", OTHER_OWNER_ID, TEST_PET_ID)
            .param("id", String.valueOf(TEST_PET_ID))
            .param("name", "Maximus")
            .param("type", "hamster")
            .param("birthDate", "2016/03/04")
        )
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/owners/" + OTHER_OWNER_ID));

        ArgumentCaptor<Pet> captor = ArgumentCaptor.forClass(Pet.class);
        verify(this.clinicService, times(1)).savePet(captor.capture());
        Pet saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(TEST_PET_ID);
        assertThat(saved.getName()).isEqualTo("Maximus");
        assertThat(saved.getOwner()).isSameAs(betty);
    }

}
