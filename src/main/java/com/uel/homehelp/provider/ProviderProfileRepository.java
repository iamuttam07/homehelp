package com.uel.homehelp.provider;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProviderProfileRepository extends JpaRepository<ProviderProfile, UUID> {

}
