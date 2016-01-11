LUPAPISTE.SidePanelModel = function(params) {
  "use strict";
  var self = this;

  self.application = lupapisteApp.models.application;
  self.authorization = lupapisteApp.models.applicationAuthModel;

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
  };

  self.toggleNoticePanel = function() {
    self.showNoticePanel(!self.showNoticePanel());
    self.showConversationPanel(false);
  };

  self.closeSidePanel = function() {
    self.showNoticePanel(false);
    self.showConversationPanel(false);
  };

  var pages = ["application", "attachment", "statement", "neighbors", "verdict"];

  var pageLoadSubscription = hub.subscribe({type: "page-load"}, function(data) {
    self.showSidePanel(_.contains(pages, pageutil.getPage()));
  });

  self.dispose = function() {
    hub.unsubscribe(pageLoadSubscription);
  };
};
