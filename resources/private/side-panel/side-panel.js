LUPAPISTE.SidePanelModel = function(authorizationModel, takeAll, newCommentRoles) {
  "use strict";
  var self = this;

  self.applicationId = ko.observable();
  self.notice = ko.observable({});
  if (LUPAPISTE.NoticeModel) {
    self.notice(new LUPAPISTE.NoticeModel());
  }
  self.showConversationPanel = ko.observable(false);
  self.showNoticePanel = ko.observable(false);
  self.unseenComments = ko.observable();
  self.authorization = authorizationModel;
  self.comment = ko.observable(comments.create(takeAll, newCommentRoles));
  self.permitType = ko.observable();
  self.authorities = ko.observableArray([]);
  self.infoRequest = ko.observable();

  var AuthorityInfo = function(id, firstName, lastName) {
    this.id = id;
    this.firstName = firstName;
    this.lastName = lastName;
  };

  function initAuthoritiesSelectList(data) {
    var authorityInfos = [];
    _.each(data || [], function(authority) {
      authorityInfos.push(new AuthorityInfo(authority.id, authority.firstName, authority.lastName));
    });
    self.authorities(authorityInfos);
  }

  self.refresh = function(application, authorities, opts) {
    self.applicationId(application.id);
    self.infoRequest(application.infoRequest);
    self.unseenComments(application.unseenComments);
    if (self.notice().refresh) {
      self.notice().refresh(application);
    }
    self.comment().refresh(application, opts && "comments" in opts ? opts.comments : undefined);
    self.permitType(application.permitType);
    initAuthoritiesSelectList(authorities);
  }

  var togglePanel = function(visible, button) {
    var panel = $("#side-panel, #side-panel-overlay");
    if(panel.hasClass("hide-side-panel")) {
      panel.toggleClass("hide-side-panel", 100);
    }
    else if(visible) {
      panel.toggleClass("hide-side-panel", 100);
    }
    $("#side-panel " + button).siblings().removeClass("active");
    $("#side-panel " + button).toggleClass("active");
  }

  self.toggleConversationPanel = function(data, event) {
    togglePanel(self.showConversationPanel(), ".btn-conversation");
    self.showConversationPanel(true);
    self.showNoticePanel(false);

    setTimeout(function() {
      // Mark comments seen after a second
      if (self.applicationId() && authorizationModel.ok("mark-seen")) {
        ajax.command("mark-seen", {id: self.applicationId(), type: "comments"})
          .success(function() {self.unseenComments(0);})
          .call();
      }}, 1000);
  };

  self.toggleNoticePanel = function(data, event) {
    togglePanel(self.showNoticePanel(), ".btn-notice");
    self.showConversationPanel(false);
    self.showNoticePanel(true);
  };

  self.hideSidePanel = function(data, event) {
    if (self.showConversationPanel()) {
      self.toggleConversationPanel();
    }
    if (self.showNoticePanel()) {
      self.toggleNoticePanel();
    }
  }
}
