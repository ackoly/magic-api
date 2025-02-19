package org.ssssssss.magicapi.provider.impl;

import org.apache.commons.lang3.StringUtils;
import org.ssssssss.magicapi.adapter.Resource;
import org.ssssssss.magicapi.model.Constants;
import org.ssssssss.magicapi.model.Group;
import org.ssssssss.magicapi.model.TreeNode;
import org.ssssssss.magicapi.provider.GroupServiceProvider;
import org.ssssssss.magicapi.utils.JsonUtils;
import org.ssssssss.magicapi.utils.PathUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 默认分组存储实现
 *
 * @author mxd
 */
public class DefaultGroupServiceProvider implements GroupServiceProvider {

	private final Map<String, Resource> mappings = new HashMap<>();
	private final Resource workspace;
	private Map<String, Group> cacheApiTree = new HashMap<>();
	private Map<String, Group> cacheFunctionTree = new HashMap<>();

	public DefaultGroupServiceProvider(Resource workspace) {
		this.workspace = workspace;
	}

	@Override
	public boolean insert(Group group) {
		if (StringUtils.isBlank(group.getId())) {
			group.setId(UUID.randomUUID().toString().replace("-", ""));
		}
		Resource directory = this.getGroupResource(group.getParentId());
		directory = directory == null ? this.getGroupResource(group.getType(), group.getName()) : directory.getDirectory(group.getName());
		if (!directory.exists() && directory.mkdir()) {
			Resource resource = directory.getResource(Constants.GROUP_METABASE);
			if (resource.write(JsonUtils.toJsonString(group))) {
				mappings.put(group.getId(), resource);
				return true;
			}
		}
		return false;
	}

	private Resource getGroupResource(String type, String name) {
		return this.workspace.getDirectory(Constants.GROUP_TYPE_API.equals(type) ? Constants.PATH_API : Constants.PATH_FUNCTION).getDirectory(name);
	}

	@Override
	public boolean update(Group group) {
		Resource oldResource = this.getGroupResource(group.getId());
		Resource newResource = this.getGroupResource(group.getParentId());
		newResource = newResource == null ? getGroupResource(group.getType(), group.getName()) : newResource.getDirectory(group.getName());
		// 重命名或移动目录
		if (oldResource.renameTo(newResource)) {
			Resource target = newResource.getResource(Constants.GROUP_METABASE);
			if (target.write(JsonUtils.toJsonString(group))) {
				mappings.put(group.getId(), target);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean delete(String groupId) {
		mappings.remove(groupId);
		return true;
	}

	@Override
	public boolean exists(Group group) {
		Resource resource = getGroupResource(group.getParentId());
		if (resource == null) {
			return getGroupResource(group.getType(), group.getName()).exists();
		}
		return resource.getDirectory(group.getName()).exists();
	}

	@Override
	public boolean containsApiGroup(String groupId) {
		return Constants.ROOT_ID.equals(groupId) || cacheApiTree.containsKey(groupId);
	}

	@Override
	public Group readGroup(Resource resource) {
		return JsonUtils.readValue(resource.read(), Group.class);
	}

	@Override
	public TreeNode<Group> apiGroupTree() {
		List<Group> groups = groupList(Constants.GROUP_TYPE_API);
		cacheApiTree = groups.stream().collect(Collectors.toMap(Group::getId, value -> value));
		clearMappings();
		return convertToTree(groups);
	}

	private void clearMappings() {
		Set<String> apiGroups = cacheApiTree.keySet();
		Set<String> functionGroups = cacheFunctionTree.keySet();
		mappings.entrySet().removeIf(entry -> !apiGroups.contains(entry.getKey()) && !functionGroups.contains(entry.getKey()));
	}

	@Override
	public TreeNode<Group> functionGroupTree() {
		List<Group> groups = groupList(Constants.GROUP_TYPE_FUNCTION);
		cacheFunctionTree = groups.stream().collect(Collectors.toMap(Group::getId, value -> value));
		clearMappings();
		return convertToTree(groups);
	}

	@Override
	public List<Group> groupList(String type) {
		Resource resource = this.workspace.getDirectory(Constants.GROUP_TYPE_API.equals(type) ? Constants.PATH_API : Constants.PATH_FUNCTION);
		resource.readAll();
		return getGroupList(resource);
	}

	private List<Group> getGroupList(Resource resource) {
		return resource.dirs().stream().map(it -> it.getResource(Constants.GROUP_METABASE)).filter(Resource::exists)
				.map(it -> {
					Group group = JsonUtils.readValue(it.read(), Group.class);
					mappings.put(group.getId(), it);
					return group;
				})
				.collect(Collectors.toList());
	}

	@Override
	public List<Group> cachedGroupList(String type) {
		Resource resource = this.workspace.getDirectory(Constants.GROUP_TYPE_API.equals(type) ? Constants.PATH_API : Constants.PATH_FUNCTION);
		return getGroupList(resource);
	}

	@Override
	public String getFullPath(String groupId) {
		StringBuilder path = new StringBuilder();
		Group group;
		while ((group = cacheFunctionTree.getOrDefault(groupId, cacheApiTree.get(groupId))) != null) {
			path.insert(0, '/' + Objects.toString(group.getPath(), ""));
			groupId = group.getParentId();
		}
		// 需要找到根节点，否则说明中间被删除了
		if (!Constants.ROOT_ID.equals(groupId)) {
			return null;
		}
		return PathUtils.replaceSlash(path.toString());
	}

	@Override
	public String getFullName(String groupId) {
		if (groupId == null || Constants.ROOT_ID.equals(groupId)) {
			return "";
		}
		StringBuilder name = new StringBuilder();
		Group group;
		while ((group = cacheFunctionTree.getOrDefault(groupId, cacheApiTree.get(groupId))) != null) {
			name.insert(0, '/' + group.getName());
			groupId = group.getParentId();
		}
		// 需要找到根节点，否则说明中间被删除了
		if (!Constants.ROOT_ID.equals(groupId)) {
			return null;
		}
		return name.substring(1);
	}

	@Override
	public Resource getGroupResource(String groupId) {
		if (groupId == null || Constants.ROOT_ID.equals(groupId)) {
			return null;
		}
		Resource resource = mappings.get(groupId);
		return resource == null ? null : resource.parent();
	}

	private TreeNode<Group> convertToTree(List<Group> groups) {
		TreeNode<Group> root = new TreeNode<>();
		root.setNode(new Group(Constants.ROOT_ID, "root"));
		convertToTree(groups, root);
		return root;
	}

	private void convertToTree(List<Group> remains, TreeNode<Group> current) {
		Group temp;
		List<TreeNode<Group>> childNodes = new LinkedList<>();
		Iterator<Group> iterator = remains.iterator();
		while (iterator.hasNext()) {
			temp = iterator.next();
			if (current.getNode().getId().equals(temp.getParentId())) {
				childNodes.add(new TreeNode<>(temp));
				iterator.remove();
			}
		}
		current.setChildren(childNodes);
		childNodes.forEach(it -> convertToTree(remains, it));
	}
}
