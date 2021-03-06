package org.politaktiv.communityselect.application;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.politaktiv.communityselect.domain.CommunitiesRepository;
import org.politaktiv.communityselect.domain.Community;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.util.HttpUtil;
import com.liferay.portal.model.User;
import com.liferay.portal.util.PortalUtil;

public class CommunityService {
    static final String COMMUNITY_DOMAIN_LIST = "COMMUNITY_DOMAIN_LIST";

    CommunitiesRepository repository = new CommunitiesRepository();
    MembershipRequestService membershipRequestService = new MembershipRequestService();
    int showOtherLimit = 10;

    public void setCommunitiesRepository(CommunitiesRepository repository) {
	this.repository = repository;
    }

    public void setMembershipRequestService(MembershipRequestService membershipRequestService) {
	this.membershipRequestService = membershipRequestService;
    }

    public void setShowOtherLimit(int showOtherLimit) {
	this.showOtherLimit = showOtherLimit;
    }

    public CommunityViewContainer initializeView(InitializeEvent initializeEvent) throws PortalException,
	    SystemException {
	CommunityViewContainer container = new CommunityViewContainer(initializeEvent.getCurrentCompanyId(), "",
		initializeEvent.getPortalState());

	container = searchCommunity(container, container.getNameToSearch());

	return container;
    }

    public CommunityViewContainer searchCommunity(CommunityViewContainer container, SearchEvent searchEvent)
	    throws PortalException, SystemException {
	if (container.isDirty(searchEvent.getPortalState())
		|| !container.getNameToSearch().equals(searchEvent.getNameToSearch())) {
	    container.setPortalState(searchEvent.getPortalState());
	    container.setNameToSearch(searchEvent.getNameToSearch());
	    container = searchCommunity(container, searchEvent.getNameToSearch());
	}
	return container;
    }

    public CommunityViewContainer calculateView(CommunityViewContainer container, PortalState currentPortalState)
	    throws PortalException, SystemException {
	if (container.isDirty(currentPortalState)) {
	    container.setPortalState(currentPortalState);
	    container = searchCommunity(container, container.getNameToSearch());
	}

	return container;
    }

    CommunityViewContainer searchCommunity(CommunityViewContainer container, String nameToSearch)
	    throws PortalException, SystemException {

	PortalState portalState = container.getPortalState();
	container.resetResults();

	List<Community> communityDomainList;
	if (nameToSearch.isEmpty()) {
	    communityDomainList = repository.findCommunitiesByCompanyId(container.getCurrentCompanyId());
	} else {
	    communityDomainList = repository.findCommunitiesByCompanyIdAndSearchString(container.getCurrentCompanyId(),
		    nameToSearch);
	}

	Set<Long> userGroupIds = null;
	User user = null;
	if (portalState.isSignedIn()) {
	    user = portalState.getUser();
	    userGroupIds = createUserGroupSet(user);
	}

	for (Community communityDomain : communityDomainList) {
	    if (portalState.isSignedIn()) {
		container = handleSignedInCase(container, portalState, userGroupIds, user, communityDomain);
	    } else {
		container = handleSignedOffCase(container, portalState, user, communityDomain);
	    }
	}

	return container;
    }

    CommunityViewContainer handleSignedInCase(CommunityViewContainer container, PortalState portalState,
	    Set<Long> userGroupIds, User user, Community communityDomain) throws SystemException {
	boolean isGroupMember = isGroupMember(userGroupIds, communityDomain);
	if (isGroupMember) {
	    container.addMemberCommunity(createMemberCommunity(portalState, communityDomain));
	} else if (communityDomain.isOpenCommunity()) {
	    container.addNonMemberOpenCommunity(createOpenCommunity(portalState, communityDomain, isGroupMember));
	}

	if (communityDomain.isOpenCommunity()) {
	    container.addOpenCommunity(createOpenCommunity(portalState, communityDomain, isGroupMember));
	} else if (communityDomain.isRestrictedCommunity()) {
	    container.addRestrictedCommunity(createOtherRestrictedCommuity(user, portalState, communityDomain,
		    isGroupMember));
	}
	return container;
    }

    CommunityViewContainer handleSignedOffCase(CommunityViewContainer container, PortalState portalState, User user,
	    Community communityDomain) throws SystemException {
	if (communityDomain.isOpenCommunity()) {
	    container.addOpenCommunity(createOpenCommunity(portalState, communityDomain, false));
	    container.addNonMemberOpenCommunity(createOpenCommunity(portalState, communityDomain, false));
	} else if (communityDomain.isRestrictedCommunity()) {
	    container.addRestrictedCommunity(createOtherRestrictedCommuity(user, portalState, communityDomain, false));
	}

	return container;
    }

    CommunityView createMemberCommunity(PortalState portalState, Community communityDomain) {

	String actionIcon = "leave";
	String actionText = "give-up";
	String action = "LEAVE";

	String urlToCommunity = calculateUrlToCommunity(portalState, communityDomain.getFriendlyUrl());
	String urlToLogo = calculateUrlToLogo(communityDomain);

	CommunityView communityView = new CommunityView(communityDomain.getName(), Long.toString(communityDomain
		.getCommunityId()), urlToCommunity, urlToLogo, communityDomain.getMemberCount(), actionIcon,
		actionText, action);

	return communityView;
    }

    CommunityView createOpenCommunity(PortalState portalState, Community communityDomain, boolean isMember) {

	String actionIcon;
	String actionText;
	String action;

	if (portalState.isSignedIn()) {
	    if (isMember) {
		actionIcon = "leave";
		actionText = "give-up";
		action = "LEAVE";
	    } else {
		actionIcon = "join";
		actionText = "participate";
		action = "JOIN";
	    }
	} else {
	    actionIcon = "status_online";
	    actionText = "please-log-in";
	    action = "LOGIN";
	}

	String urlToCommunity = calculateUrlToCommunity(portalState, communityDomain.getFriendlyUrl());
	String urlToLogo = calculateUrlToLogo(communityDomain);

	CommunityView communityView = new CommunityView(communityDomain.getName(), Long.toString(communityDomain
		.getCommunityId()), urlToCommunity, urlToLogo, communityDomain.getMemberCount(), actionIcon,
		actionText, action);

	return communityView;
    }

    CommunityView createOtherRestrictedCommuity(User user, PortalState portalState, Community communityDomain,
	    boolean isMember) throws SystemException {

	String actionIcon;
	String actionText;
	String action;

	if (portalState.isSignedIn()) {
	    boolean isUserMembershipRequestPending = membershipRequestService.isUserMembershipRequestPending(
		    user.getUserId(), communityDomain.getCommunityId());

	    if (isMember) {
		actionIcon = "leave";
		actionText = "give-up";
		action = "LEAVE";
	    } else if (isUserMembershipRequestPending) {
		actionIcon = "checked";
		actionText = "membership-requested";
		action = "JOIN";
	    } else {
		actionIcon = "join";
		actionText = "request-membership";
		action = "JOIN";
	    }
	} else {
	    actionIcon = "status_online";
	    actionText = "please-log-in";
	    action = "LOGIN";
	}

	String urlToCommunity = calculateUrlToCommunity(portalState, communityDomain.getFriendlyUrl());
	String urlToLogo = calculateUrlToLogo(communityDomain);

	CommunityView communityView = new CommunityView(communityDomain.getName(), Long.toString(communityDomain
		.getCommunityId()), urlToCommunity, urlToLogo, communityDomain.getMemberCount(), actionIcon,
		actionText, action);
	return communityView;
    }

    String calculateUrlToLogo(Community communityDomain) {
	String urlToLogo;
	if (communityDomain.hasLogo()) {
	    urlToLogo = "/documents/" + communityDomain.getCommunityId() + "/" + communityDomain.getLogoFolderId()
		    + "/LOGO?imageThumbnail=1";
	} else {
	    urlToLogo = "/images/building.png";
	}
	return urlToLogo;
    }

    String calculateUrlToCommunity(PortalState portalState, String communityUrl) {
	String urlToCommunity = portalState.getPortalUrl() + (portalState.isI18n() ? portalState.getI18nPath() : "")
		+ PortalUtil.getPathFriendlyURLPublic() + communityUrl;
	if (null != portalState.getDoAsUserId() && !"".equals(portalState.getDoAsUserId())) {
	    urlToCommunity = HttpUtil.addParameter(urlToCommunity, "doAsUserId", portalState.getDoAsUserId());
	}
	return urlToCommunity;
    }

    Set<Long> createUserGroupSet(User user) throws PortalException, SystemException {
	long[] userGroupIdArray = user.getGroupIds();
	Set<Long> userGroupIds = new HashSet<Long>();
	for (int i = 0; i < userGroupIdArray.length; i++) {
	    userGroupIds.add(userGroupIdArray[i]);
	}
	return userGroupIds;
    }

    boolean isGroupMember(Set<Long> userGroupIds, Community communityDomain) {
	return userGroupIds.contains(communityDomain.getCommunityId());
    }

    public CommunityViewContainer joinCommunity(CommunityViewContainer container, JoinEvent event)
	    throws PortalException, SystemException {
	repository.joinCommunity(event.getUserId(), event.getCommunityId());
	container.setPortalState(event.getPortalState());
	container = searchCommunity(container, container.getNameToSearch());
	return container;
    }

    public CommunityViewContainer leaveCommunity(CommunityViewContainer container, LeaveEvent event)
	    throws PortalException, SystemException {
	repository.leaveCommunity(event.getUserId(), event.getCommunityId());
	container.setPortalState(event.getPortalState());
	container = searchCommunity(container, container.getNameToSearch());
	return container;
    }

    public CommunityViewContainer requestCommunityMembership(CommunityViewContainer container,
	    RequestMembershipEvent event) throws PortalException, SystemException {
	repository.requestMembershipToCommunity(event.getUserId(), event.getCompanyId(), event.getCommunityId(),
		event.getGuestUserId());
	container.setPortalState(event.getPortalState());
	container = searchCommunity(container, container.getNameToSearch());
	return container;
    }
}
