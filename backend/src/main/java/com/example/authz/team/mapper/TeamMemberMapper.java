package com.example.authz.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.authz.team.dto.TeamMemberVO;
import com.example.authz.team.entity.TeamMember;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 团队成员 Mapper（sys_team_member）。
 * <p>
 * 基础 CRUD 由 MyBatis-Plus {@link BaseMapper} 提供；
 * 复杂查询（JOIN 用户信息）见 resources/mapper/TeamMemberMapper.xml。
 *
 * @author Nickel
 * @since 2026-09-02
 */
public interface TeamMemberMapper extends BaseMapper<TeamMember> {

    /**
     * 查询某团队全部成员关联的用户信息，按加入时间倒序。
     *
     * @param teamId 团队主键
     * @return 成员视图对象列表
     */
    List<TeamMemberVO> selectMembersByTeamId(@Param("teamId") Long teamId);
}