package com.budgetops.backend.billing.entity;

import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.domain.user.entity.Member;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workspace")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private Member owner;

    @ManyToMany
    @JoinTable(
        name = "workspace_member",
        joinColumns = @JoinColumn(name = "workspace_id"),
        inverseJoinColumns = @JoinColumn(name = "member_id")
    )
    @Builder.Default
    private List<Member> members = new ArrayList<>();

    @OneToMany(mappedBy = "workspace", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AwsAccount> awsAccounts = new ArrayList<>();

    // Payment와 Billing은 Member(사용자) 단위로 관리됩니다.
    // Workspace가 아닌 Member와 OneToOne 관계를 맺습니다.

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 멤버 수 반환
     */
    public int getMemberCount() {
        return members != null ? members.size() : 0;
    }

    /**
     * 멤버 추가
     */
    public void addMember(Member member) {
        if (members == null) {
            members = new ArrayList<>();
        }
        members.add(member);
    }

    /**
     * 멤버 제거
     */
    public void removeMember(Member member) {
        if (members != null) {
            members.remove(member);
        }
    }
}
