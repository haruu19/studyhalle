package com.studyhalle.account;

import com.studyhalle.account.form.SignUpForm;
import com.studyhalle.domain.Account;
import com.studyhalle.settings.form.Notifications;
import com.studyhalle.settings.form.Profile;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class AccountService implements UserDetailsService {
    private final AccountRepository accountRepository;
    private final JavaMailSender javaMailSender;
    private final PasswordEncoder passwordEncoder;
    private final ModelMapper modelMapper;
    public Account processNewAccount(SignUpForm signUpForm) {
        Account newAccount = saveNewAccount(signUpForm);
        newAccount.generateEmailCheckToken();
        sendSignUpConfirmEmail(newAccount);
        return newAccount;
    }

    private Account saveNewAccount(SignUpForm signUpForm) {
        Account account = Account.builder()
                .email(signUpForm.getEmail())
                .nickname(signUpForm.getNickname())
                .password(passwordEncoder.encode(signUpForm.getPassword()))
                .studyCreatedByWeb(true)
                .studyEnrollmentResultByWeb(true)
                .studyUpdatedByWeb(true)
                .build();
        return accountRepository.save(account);
    }

    public void sendSignUpConfirmEmail(Account newAccount) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(newAccount.getEmail());
        mailMessage.setSubject("스터디할래, 회원가입 인증");
        mailMessage.setText("/check-email-token?token=" + newAccount.getEmailCheckToken() +
                "&email=" + newAccount.getEmail());
        javaMailSender.send(mailMessage);
    }

    public void login(Account account) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                new UserAccount(account),
                account.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(token);

//        정석은 이렇게 사용한다.
//        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
//                username, password);
//        Authentication authentication = authenticationManager.authenticate(token);
//        SecurityContext context = SecurityContextHolder.getContext();
//        context.setAuthentication(authentication);
    }

    @Transactional(readOnly = true)
    @Override
    public UserDetails loadUserByUsername(String emailOrNickname) throws UsernameNotFoundException {
        Account account = accountRepository.findByEmail(emailOrNickname);
        if(account == null) {
            account = accountRepository.findByNickname(emailOrNickname);
        }

        if(account == null) {
            throw new UsernameNotFoundException(emailOrNickname);
        }

        return new UserAccount(account);
    }

    public void completeSignUp(Account account) {
        account.completeSignUp();
        login(account);
    }

    public void updateProfile(Account account, Profile profile) {
        modelMapper.map(profile, account);
        accountRepository.save(account); // account 파라미터가 detached 상태, 영속성 컨테이너의 관리를 받고 있지 않기 때문에 save 해주면 merge 된다.
    }

    public void updatePassword(Account account, String newPassword) {
        account.setPassword(passwordEncoder.encode(newPassword));
        accountRepository.save(account); // merge
    }

    public void updateNotifications(Account account, Notifications notifications) {
        modelMapper.map(notifications, account);
        accountRepository.save(account);
    }
}
