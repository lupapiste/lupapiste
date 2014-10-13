LUPAPISTE.SidePanelModel = function() {
  "use strict";
  var self = this;

  self.typeId = undefined;

  self.application = ko.observable();
  self.applicationId = ko.observable();
  self.notice = ko.observable({});
  self.attachmentId = ko.observable();
  if (LUPAPISTE.NoticeModel) {
    self.notice(new LUPAPISTE.NoticeModel());
  }
  self.showConversationPanel = ko.observable(false);
  self.showNoticePanel = ko.observable(false);
  self.unseenComments = ko.observable();
  self.authorization = authorization.create();
  self.comment = ko.observable(comments.create());
  self.permitType = ko.observable();
  self.authorities = ko.observableArray([]);
  self.infoRequest = ko.observable();
  self.authentication = ko.observable();
  self.authorities = ko.observable();
  self.mainConversation = ko.observable(true);
  self.showHelp = ko.observable();

  self.sidePanelVisible = ko.computed(function() {
    return self.showConversationPanel() || self.showNoticePanel();
  });

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

  self.refresh = function(application, authorities) {
    // TODO applicationId, inforequest etc. could be computed
    if(application && authorities) {
      self.application(application);
      self.authorities(authorities);
      self.applicationId(application.id);
      self.infoRequest(application.infoRequest);
      self.unseenComments(application.unseenComments);
      if (self.notice().refresh) {
        self.notice().refresh(application);
      }
      self.permitType(self.application().permitType);
      initAuthoritiesSelectList(self.authorities());
    }

    if (self.application()) {
      var type = pageutil.getPage();
      self.mainConversation(true);
      switch(type) {
        case "attachment":
          self.mainConversation(false);
        case "statement":
          self.comment().refresh(self.application(), false, {type: type, id: pageutil.lastSubPage()});
          break;
        case "verdict":
          self.comment().refresh(self.application(), false, {type: type, id: pageutil.lastSubPage()}, ["authority"]);
          break;
        default:
          self.comment().refresh(self.application(), true);
          break;
      }
    }
  };

  self.toggleConversationPanel = function(data, event) {
    self.showConversationPanel(!self.showConversationPanel());
    self.showNoticePanel(false);
    // Set focus to new comment textarea
    self.comment().isSelected(self.showConversationPanel());

    setTimeout(function() {
      // Mark comments seen after a second
      if (self.applicationId() && self.authorization.ok("mark-seen")) {
        ajax.command("mark-seen", {id: self.applicationId(), type: "comments"})
          .success(function() {self.unseenComments(0);})
          .call();
      }}, 1000);
  };

  self.toggleNoticePanel = function(data, event) {
    self.showNoticePanel(!self.showNoticePanel());
    self.showConversationPanel(false);
  };

  self.closeSidePanel = function(data, event) {
    if (self.showConversationPanel()) {
      self.toggleConversationPanel();
    }
    if (self.showNoticePanel()) {
      self.toggleNoticePanel();
    }
  };

  self.toggleHelp = function() {
    self.showHelp(!self.showHelp());
  }

  var pages = ["application","attachment","statement","neighbors","verdict"];

  hub.subscribe({type: "page-change"}, function() {
    if(_.contains(pages, pageutil.getPage())) {
      self.refresh();
      $("#side-panel-template").addClass("visible");
    }
  });

  repository.loaded(pages, function(application, applicationDetails) {
    self.authorization.refreshWithCallback({id: applicationDetails.application.id}, function() {
      self.refresh(application, applicationDetails.authorities);
    });
  });
};

$(function() {
  var sidePanel = new LUPAPISTE.SidePanelModel();
  $(document).keyup(function(e) {
    // esc hides the side panel
    if (e.keyCode === 27) { sidePanel.closeSidePanel(); };
  });
  $("#side-panel-template").applyBindings(sidePanel);
});
