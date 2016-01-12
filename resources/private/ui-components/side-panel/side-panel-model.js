LUPAPISTE.SidePanelModel = function(params) {
  "use strict";
  var self = this;

  self.application = lupapisteApp.models.application;
  self.authorization = lupapisteApp.models.applicationAuthModel;
  self.authorities = ko.observable();

  self.enableSidePanel = ko.observable(false);
  self.showSidePanel = ko.observable(false);
  self.sidePanelOpen = ko.observable(false);

  self.showConversationPanel = ko.observable(false);
  self.showNoticePanel = ko.observable(false);

  self.showHelp = ko.observable(false);

  self.sidePanelOpen = ko.pureComputed(function() {
    return self.showConversationPanel() || self.showNoticePanel();
  });

  self.unseenComments = ko.pureComputed(function() {
    return util.getIn(self, ["application", "unseenComments"]);
  });

  self.permitType = ko.pureComputed(function() {
    return util.getIn(self, ["application", "permitType"]);
  });

  self.hideHelp = function() {
    self.showHelp(false);
  };

  self.toggleConversationPanel = function() {
    self.showConversationPanel(!self.showConversationPanel());
    self.showNoticePanel(false);

    // TODO move to service
    if (self.showConversationPanel()) {
      setTimeout(function() {
        // Mark comments seen after a second
        if (self.application.id() && self.authorization.ok("mark-seen")) {
          ajax.command("mark-seen", {id: self.application.id(), type: "comments"})
          .success(function() {self.application.unseenComments(0);})
          .call();
        }}, 1000);
    }
  };

  self.toggleNoticePanel = function() {
    self.showNoticePanel(!self.showNoticePanel());
    self.showConversationPanel(false);
  };

  self.closeSidePanel = function() {
    self.showNoticePanel(false);
    self.showConversationPanel(false);
  };

  var pages = ["applications", "application", "attachment", "statement", "neighbors", "verdict"];

  var pageLoadSubscription = hub.subscribe({type: "page-load"}, function(data) {
    self.showSidePanel(_.contains(_.without(pages, "applications"), pageutil.getPage()));
    self.enableSidePanel(self.application && _.contains(pages, pageutil.getPage()));
  });

  self.dispose = function() {
    hub.unsubscribe(pageLoadSubscription);
  };

  // TODO move to service
  ko.computed(function() {
    if (self.application && self.application.id() && self.authorization.ok("application-authorities") ) {
      ajax.query("application-authorities", {id: self.application.id()})
      .success(function(resp) {
        self.authorities(resp.authorities);
      })
      .call();
    }
  });
};
