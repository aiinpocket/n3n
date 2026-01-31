package com.aiinpocket.n3n.auth.repository;

import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.base.BaseRepositoryTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmail_existingUser_returnsUser() {
        // Given
        User user = User.builder()
                .email("test@example.com")
                .passwordHash("hashedPassword")
                .name("Test User")
                .status("active")
                .emailVerified(true)
                .loginAttempts(0)
                .build();
        entityManager.persist(user);
        flushAndClear();

        // When
        Optional<User> result = userRepository.findByEmail("test@example.com");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("test@example.com");
        assertThat(result.get().getName()).isEqualTo("Test User");
    }

    @Test
    void findByEmail_nonExistingUser_returnsEmpty() {
        // When
        Optional<User> result = userRepository.findByEmail("nonexistent@example.com");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void existsByEmail_existingEmail_returnsTrue() {
        // Given
        User user = User.builder()
                .email("exists@example.com")
                .passwordHash("hashedPassword")
                .name("Existing User")
                .status("active")
                .emailVerified(true)
                .loginAttempts(0)
                .build();
        entityManager.persist(user);
        flushAndClear();

        // When
        boolean exists = userRepository.existsByEmail("exists@example.com");

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void existsByEmail_nonExistingEmail_returnsFalse() {
        // When
        boolean exists = userRepository.existsByEmail("notexists@example.com");

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void save_newUser_persistsSuccessfully() {
        // Given
        User user = User.builder()
                .email("new@example.com")
                .passwordHash("hashedPassword")
                .name("New User")
                .status("pending")
                .emailVerified(false)
                .loginAttempts(0)
                .build();

        // When
        User saved = userRepository.save(user);
        flushAndClear();

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<User> found = userRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void count_emptyDatabase_returnsZero() {
        // When
        long count = userRepository.count();

        // Then
        assertThat(count).isZero();
    }

    @Test
    void count_withUsers_returnsCorrectCount() {
        // Given
        User user1 = User.builder()
                .email("user1@example.com")
                .passwordHash("hash")
                .name("User 1")
                .status("active")
                .build();
        User user2 = User.builder()
                .email("user2@example.com")
                .passwordHash("hash")
                .name("User 2")
                .status("active")
                .build();
        entityManager.persist(user1);
        entityManager.persist(user2);
        entityManager.flush();

        // When
        long count = userRepository.count();

        // Then
        assertThat(count).isEqualTo(2);
    }
}
