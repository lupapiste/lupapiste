LUPAPISTE.SidePanelModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.sidePanelService = lupapisteApp.services.sidePanelService;

  self.application = self.sidePanelService.application;
  self.authorization = self.sidePanelService.authorization;

  self.enableSidePanel = ko.observable(false);
  self.showSidePanel = ko.observable(false);
  self.sidePanelOpen = ko.observable(false);

  self.showConversationPanel = ko.observable(false);
  self.showNoticePanel = ko.observable(false);

  self.showHelp = ko.observable(false);

  self.currentPage = self.sidePanelService ? self.sidePanelService.currentPage : ko.observable();

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

    if (self.showConversationPanel()) {
      self.sendEvent("SidePanelService", "UnseenCommentsSeen");
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

  ko.computed(function() {
    self.showSidePanel(_.contains(_.without(pages, "applications"), self.currentPage()));
    self.enableSidePanel(self.application && _.contains(pages, self.currentPage()));
  });

  self.addEventListener("side-panel", "show-conversation", function() {
    if (!self.showConversationPanel()) {
      self.toggleConversationPanel();
    }
  });
};
