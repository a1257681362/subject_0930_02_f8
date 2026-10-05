package com.evops.aqua.harv.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.harv.dto.GradeVersionPublishRequest;
import com.evops.aqua.harv.entity.HarvChangeEvidence;
import com.evops.aqua.harv.entity.HarvGradeRule;
import com.evops.aqua.harv.entity.HarvGradeVersion;
import com.evops.aqua.harv.mapper.HarvGradeRuleMapper;
import com.evops.aqua.harv.mapper.HarvGradeVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 分级规则版本：按苗种发布整套规格（规格代码 + 单尾重量闭区间）。
 * 新版本发布后同苗种旧版本自动停用；规则行永不删除，已冻结到订单/预约的版本与快照永久可查。
 */
@Service
public class HarvGradeService {

    private final HarvGradeVersionMapper versionMapper;
    private final HarvGradeRuleMapper ruleMapper;
    private final HarvEvidenceService evidenceService;

    public HarvGradeService(HarvGradeVersionMapper versionMapper,
                            HarvGradeRuleMapper ruleMapper,
                            HarvEvidenceService evidenceService) {
        this.versionMapper = versionMapper;
        this.ruleMapper = ruleMapper;
        this.evidenceService = evidenceService;
    }

    /**
     * 发布新版本：校验重量区间合法且不重叠；同苗种旧生效版本批量停用；requestId 幂等。
     */
    @Transactional(rollbackFor = Exception.class)
    public HarvGradeVersion publish(GradeVersionPublishRequest request) {
        HarvGradeVersion duplicated = versionMapper.selectOne(new QueryWrapper<HarvGradeVersion>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        List<HarvGradeRule> rules = validateRules(request.getRules());

        String versionNo = nextVersionNo(request.getSpecies());
        HarvGradeVersion version = new HarvGradeVersion();
        version.setVersionNo(versionNo);
        version.setSpecies(request.getSpecies());
        version.setStatus(HarvGradeVersion.STATUS_ACTIVE);
        version.setPublishedBy(request.getPublishedBy());
        version.setPublishedTime(LocalDateTime.now());
        version.setRequestId(request.getRequestId());
        version.setRemark(request.getRemark());
        versionMapper.insert(version);
        for (HarvGradeRule rule : rules) {
            rule.setVersionId(version.getId());
            ruleMapper.insert(rule);
        }

        List<HarvGradeVersion> oldActives = versionMapper.selectList(new QueryWrapper<HarvGradeVersion>()
                .eq("species", request.getSpecies())
                .eq("status", HarvGradeVersion.STATUS_ACTIVE)
                .ne("id", version.getId()));
        List<String> oldNos = new ArrayList<>();
        for (HarvGradeVersion old : oldActives) {
            oldNos.add(old.getVersionNo());
            old.setStatus(HarvGradeVersion.STATUS_INACTIVE);
            versionMapper.updateById(old);
        }
        evidenceService.record(HarvChangeEvidence.ENTITY_GRADE_VERSION, version.getId(),
                HarvChangeEvidence.ACTION_GRADE_PUBLISH, request.getRequestId(), request.getPublishedBy(),
                null, version, "发布分级版本" + versionNo + "（苗种：" + request.getSpecies() + "），规格"
                        + rules.size() + "条；旧版本停用："
                        + (oldNos.isEmpty() ? "无" : String.join("、", oldNos)));
        return version;
    }

    public HarvGradeVersion getOrThrow(Long versionId) {
        HarvGradeVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new IllegalArgumentException("分级规则版本不存在，id=" + versionId);
        }
        return version;
    }

    /** 取苗种当前唯一生效版本；没有则拒绝（必须先发布分级规则）。 */
    public HarvGradeVersion activeVersionOf(String species) {
        List<HarvGradeVersion> actives = versionMapper.selectList(new QueryWrapper<HarvGradeVersion>()
                .eq("species", species)
                .eq("status", HarvGradeVersion.STATUS_ACTIVE)
                .orderByDesc("id"));
        if (actives.isEmpty()) {
            throw new IllegalArgumentException("苗种「" + species + "」尚未发布生效的分级规则版本，无法建立捕捞订单");
        }
        return actives.get(0);
    }

    public List<HarvGradeRule> rulesOf(Long versionId) {
        return ruleMapper.selectList(new QueryWrapper<HarvGradeRule>()
                .eq("version_id", versionId).orderByAsc("id"));
    }

    /** 按版本+规格代码取规则行（实际规格必须落在订单冻结的版本内）。 */
    public HarvGradeRule ruleOf(Long versionId, String gradeCode) {
        return ruleMapper.selectOne(new QueryWrapper<HarvGradeRule>()
                .eq("version_id", versionId).eq("grade_code", gradeCode).last("limit 1"));
    }

    public List<HarvGradeVersion> list(String species) {
        QueryWrapper<HarvGradeVersion> wrapper = new QueryWrapper<>();
        wrapper.eq(species != null && !species.trim().isEmpty(), "species", species);
        wrapper.orderByAsc("id");
        return versionMapper.selectList(wrapper);
    }

    private List<HarvGradeRule> validateRules(List<GradeVersionPublishRequest.GradeRuleRequest> requests) {
        List<HarvGradeRule> rules = new ArrayList<>();
        for (GradeVersionPublishRequest.GradeRuleRequest line : requests) {
            if (line.getMinWeightKg().compareTo(line.getMaxWeightKg()) > 0) {
                throw new IllegalArgumentException("规格" + line.getGradeCode()
                        + "重量下限不能大于上限：" + line.getMinWeightKg() + "~" + line.getMaxWeightKg());
            }
            HarvGradeRule rule = new HarvGradeRule();
            rule.setGradeCode(line.getGradeCode());
            rule.setGradeName(line.getGradeName());
            rule.setMinWeightKg(line.getMinWeightKg());
            rule.setMaxWeightKg(line.getMaxWeightKg());
            rules.add(rule);
        }
        for (int i = 0; i < rules.size(); i++) {
            for (int j = i + 1; j < rules.size(); j++) {
                HarvGradeRule a = rules.get(i);
                HarvGradeRule b = rules.get(j);
                boolean overlap = a.getMinWeightKg().compareTo(b.getMaxWeightKg()) <= 0
                        && b.getMinWeightKg().compareTo(a.getMaxWeightKg()) <= 0;
                if (overlap) {
                    throw new IllegalArgumentException("规格重量区间重叠：" + a.getGradeCode()
                            + "(" + a.getMinWeightKg() + "~" + a.getMaxWeightKg() + ") 与 "
                            + b.getGradeCode() + "(" + b.getMinWeightKg() + "~" + b.getMaxWeightKg() + ")");
                }
            }
        }
        return rules;
    }

    private String nextVersionNo(String species) {
        Long count = versionMapper.selectCount(new QueryWrapper<HarvGradeVersion>()
                .eq("species", species));
        return "GV-" + species + "-" + (count + 1);
    }
}
