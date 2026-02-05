package prism.db.mappers;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import prism.api.Transition;
import prism.core.Model;
import prism.core.Namespace;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;

/**
 * Maps database output to Edge Objects
 */
public class TransitionMapper implements RowMapper<Transition> {

    private final DistributionMapper distributionMapper;

    private final RewardMapper rewardMapper;
    private final PropertyMapper propertyMapper;

    private final ScheduleMapper scheduleMapper;

    private final Map<String, String> map;

    private final HashMap<String, String> colorMap;

    public TransitionMapper(Model model){
        this.distributionMapper = new DistributionMapper();
        this.propertyMapper = new PropertyMapper(model.getProperties());
        this.rewardMapper = new RewardMapper(model);
        this.scheduleMapper = new ScheduleMapper(model.getSchedulers());
        this.map = null;
        this.colorMap = model.getColors();
    }

    public TransitionMapper(Model model, Map<String, String> map){
        distributionMapper = new DistributionMapper();
        this.propertyMapper = null;
        this.rewardMapper = null;
        this.scheduleMapper = null;
        this.map = map;
        this.colorMap = model.getColors();
    }

    @Override
    public Transition map(final ResultSet rs, final StatementContext ctx) throws SQLException {
        if (propertyMapper != null){
            if (!colorMap.isEmpty()){
                return new Transition(rs.getString(Namespace.ENTRY_T_ID), rs.getString(Namespace.ENTRY_T_OUT), rs.getString(Namespace.ENTRY_T_ACT), distributionMapper.map(rs, ctx), rewardMapper.map(rs, ctx), propertyMapper.map(rs, ctx), scheduleMapper.map(rs, ctx), map, colorMap.get(rs.getString(Namespace.ENTRY_T_ID)));
            }else{
                return new Transition(rs.getString(Namespace.ENTRY_T_ID), rs.getString(Namespace.ENTRY_T_OUT), rs.getString(Namespace.ENTRY_T_ACT), distributionMapper.map(rs, ctx), rewardMapper.map(rs, ctx), propertyMapper.map(rs, ctx), scheduleMapper.map(rs, ctx), map);
            }
        }
        else {
            return new Transition(rs.getString(Namespace.ENTRY_T_ID), rs.getString(Namespace.ENTRY_T_OUT), rs.getString(Namespace.ENTRY_T_ACT), distributionMapper.map(rs, ctx), null, null, null, map);
        }
    }
}
