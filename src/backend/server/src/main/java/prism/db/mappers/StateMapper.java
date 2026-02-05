package prism.db.mappers;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import prism.PrismLangException;
import prism.api.State;
import prism.core.Model;
import prism.core.Namespace;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.HashMap;

/**
 * Maps database output to Node Objects
 */
public class StateMapper implements RowMapper<State> {

    private final Model model;

    private final ViewMapper viewMapper;

    private final PropertyMapper propertyMapper;

    private final RewardMapper rewardMapper;

    private final HashMap<String, String> colorMap;


    public StateMapper(Model model) {
        this.model = model;
        this.viewMapper = new ViewMapper();
        this.propertyMapper = new PropertyMapper(model.getProperties());
        this.rewardMapper = new RewardMapper(model);
        this.colorMap = model.getColors();
    }

    @Override
    public State map(final ResultSet rs, final StatementContext ctx) throws SQLException {
        //if (views == null) {
            try {
                if (!colorMap.isEmpty()){
                    return new State(rs.getString(Namespace.ENTRY_S_ID), rs.getString(Namespace.ENTRY_S_NAME), model.getModelParser().parseParameters(rs.getString(Namespace.ENTRY_S_NAME)), model.getLabelMap(model.getModelParser().parseState(rs.getString(Namespace.ENTRY_S_NAME))), rewardMapper.map(rs, ctx), propertyMapper.map(rs, ctx), colorMap.get(rs.getString(Namespace.ENTRY_S_NAME)));
                }
                return new State(rs.getString(Namespace.ENTRY_S_ID), rs.getString(Namespace.ENTRY_S_NAME), model.getModelParser().parseParameters(rs.getString(Namespace.ENTRY_S_NAME)), model.getLabelMap(model.getModelParser().parseState(rs.getString(Namespace.ENTRY_S_NAME))), rewardMapper.map(rs, ctx), propertyMapper.map(rs, ctx));
            }catch (PrismLangException e) {
                return new State(rs.getString(Namespace.ENTRY_S_ID), rs.getString(Namespace.ENTRY_S_NAME), new TreeMap<>(), new TreeMap<>(), rewardMapper.map(rs, ctx), propertyMapper.map(rs, ctx));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        //}
        //return new State(rs.getString(Namespace.ENTRY_C_NAME), views.stream().map(c -> Long.toString(c.getId())).collect(Collectors.toList()), viewMapper.map(rs, ctx));
    }
}
