/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.cellar.core.control;

import java.util.Set;

import org.apache.karaf.cellar.core.Configurations;
import org.apache.karaf.cellar.core.Group;
import org.apache.karaf.cellar.core.Node;
import org.apache.karaf.cellar.core.command.CommandHandler;

/**
 * Manager group command handler.
 */
public class ManageGroupCommandHandler extends CommandHandler<ManageGroupCommand, ManageGroupResult> {

    public static final String SWITCH_ID = "org.apache.karaf.cellar.command.managegroup.switch";
    private final Switch commandSwitch = new BasicSwitch(SWITCH_ID);

    @Override
    public ManageGroupResult execute(ManageGroupCommand command) {

        ManageGroupResult result = new ManageGroupResult(command.getId());
        ManageGroupAction action = command.getAction();

        String targetGroupName = command.getGroupName();

        if (ManageGroupAction.JOIN.equals(action)) {
            groupManager.joinGroup(targetGroupName);
        } else if (ManageGroupAction.QUIT.equals(action)) {
            groupManager.quitGroup(targetGroupName);
            if (groupManager.listLocalGroups().isEmpty()) {
                groupManager.joinGroup(Configurations.DEFAULT_GROUP_NAME);
            }
        } else if (ManageGroupAction.PURGE.equals(action)) {
            purgeGroups();
            groupManager.joinGroup(Configurations.DEFAULT_GROUP_NAME);
        } else if (ManageGroupAction.SET.equals(action)) {
            Group localGroup = groupManager.listLocalGroups().iterator().next();
            groupManager.quitGroup(localGroup.getName());
            groupManager.joinGroup(targetGroupName);
        }

        addGroupListToResult(result);

        return result;
    }

    /**
     * Add the {@link Group} list to the result.
     *
     * @param result the result where to add the group list.
     */
    public void addGroupListToResult(ManageGroupResult result) {
        Set<Group> groups = groupManager.listAllGroups();

        for (Group g : groups) {
            if (g.getName() != null && !g.getName().isEmpty()) {
                result.getGroups().add(g);
            }
        }
    }

    /**
     * Remove {@link Node} from all {@link Group}s.
     */
    public void purgeGroups() {
        Node node = clusterManager.getNode();
        Set<String> groupNames = groupManager.listGroupNames(node);
        if (groupNames != null && !groupNames.isEmpty()) {
            for (String targetGroupName : groupNames) {
                groupManager.quitGroup(targetGroupName);
            }
        }
    }

    @Override
    public Class<ManageGroupCommand> getType() {
        return ManageGroupCommand.class;
    }

    @Override
    public Switch getSwitch() {
        return commandSwitch;
    }

}
