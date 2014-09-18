LUPAPISTE.SidePanelModel = function() {
  "use strict";
  var self = this;

  self.typeId = undefined;

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

  self.showSidePanel = ko.computed(function() {
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
    self.applicationId(application.id);
    self.infoRequest(application.infoRequest);
    self.unseenComments(application.unseenComments);
    if (self.notice().refresh) {
      self.notice().refresh(application);
    }
    var type = pageutil.getPage();
    switch(type) {
      case "attachment":
      case "statement":
        self.comment().refresh(application, false, {type: type, id: pageutil.lastSubPage()});
        break;
      case "verdict":
        self.comment().refresh(application, false, {type: type, id: pageutil.lastSubPage()}, ["authority"]);
        break;
      default:
        self.comment().refresh(application, true);
        break;
    }
    self.permitType(application.permitType);
    initAuthoritiesSelectList(authorities);
  }

  self.toggleConversationPanel = function(data, event) {
    self.showConversationPanel(!self.showConversationPanel());
    self.showNoticePanel(false);

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

  self.hideSidePanel = function(data, event) {
    if (self.showConversationPanel()) {
      self.toggleConversationPanel();
    }
    if (self.showNoticePanel()) {
      self.toggleNoticePanel();
    }
  }

  var pages = ["application","attachment","statement","neighbors","verdict"];

  hub.subscribe({type: "page-change"}, function() {
    if(_.contains(pages, pageutil.getPage())) {
      $("#side-panel-template").addClass("visible");
    }
  });

  repository.loaded(pages, function(application, applicationDetails) {
    self.authorization.refreshWithCallback({id: applicationDetails.application.id}, function() {
      self.refresh(application, applicationDetails.authorities);
    });
  });
}

ko.bindingHandlers.slideVisible = {
    init: function(element, valueAccessor) {
        var value = valueAccessor();
        $(element).toggleClass("hide-side-panel", !value(), 100);
    },
    update: function(element, valueAccessor) {
        var value = valueAccessor();
        $(element).toggleClass("hide-side-panel", !value(), 100);
    }
};

$(function() {
  var sidePanel = new LUPAPISTE.SidePanelModel();
  $(document).keyup(function(e) {
    // esc hides the side panel
    if (e.keyCode == 27) { sidePanel.hideSidePanel() };
  });
  $("#side-panel-template").applyBindings(sidePanel);
});
