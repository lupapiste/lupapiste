LUPAPISTE.SidePanelModel = function(params) {
  "use strict";
  var self = this;

  self.application = lupapisteApp.models.application;
  self.authorization = lupapisteApp.models.applicationAuthModel;

  self.unseenComments = ko.observable();

  self.showSidePanel = ko.observable(true);
  self.sidePanelOpen = ko.observable();

  self.showConversationPanel = ko.observable(false);
  self.showNoticePanel = ko.observable(false);


  // conversation component
  self.showHelp = ko.observable();
  self.permitType = ko.observable('R');

  self.sidePanelOpen = ko.computed(function() {
    return self.showConversationPanel() || self.showNoticePanel();
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

};
