package com.example.springbootbasiclogin.service;

import com.example.springbootbasiclogin.repo.RoleRepository;
import com.example.springbootbasiclogin.entity.Roles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;


@Service
public class RolesServiceImpl implements RoleService {

    private final RoleRepository roleRepository;

    @Autowired
    public RolesServiceImpl(RoleRepository theRoleRepository) {
        roleRepository= theRoleRepository;
    }

    @Override
    public Flux<Roles> findAll() {
        return roleRepository.findAll();
    }

    @Override
    public Mono<Roles> findById(int theId) {
        return roleRepository.findById(theId);
    }

    @Override
    public Mono<Roles> save(Roles theRoles) {
        return roleRepository.save(theRoles);
    }

    @Override
    public Mono<Roles> update(int id, Roles theRoles) {
        return roleRepository.findById(id).map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(optionalUser -> {
                    if(optionalUser.isPresent()){
                        theRoles.setRoleId(id);
                        return roleRepository.save(theRoles);
                    }
                    //else empty
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> deleteById(int theId) {
        return roleRepository.deleteById(theId);
    }
}


