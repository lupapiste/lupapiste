LUPAPISTE.SidePanelModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.sidePanelService = util.getIn(lupapisteApp, ["services", "sidePanelService"], {});
  self.application      = self.sidePanelService.application;
  self.authorization    = self.sidePanelService.authorization;
  self.currentPage      = self.sidePanelService.currentPage;

  self.showSidePanel = self.sidePanelService.showSidePanel;

  self.enableSidePanel = self.sidePanelService.enableSidePanel;

  self.showConversationPanel = self.sidePanelService.showConversationPanel;
  self.showNoticePanel = self.sidePanelService.showNoticePanel;
  self.showCompanyNotesPanel = self.sidePanelService.showCompanyNotesPanel;
  self.showInfoPanel = self.sidePanelService.showInfoPanel;

  var panelFlags = [self.showConversationPanel,
                    self.showNoticePanel,
                    self.showCompanyNotesPanel,
                    self.showInfoPanel];

  function flagOff( obs ) {
    return obs( false );
  }

  self.showHelp = ko.observable(false);

  self.sidePanelOpen = ko.pureComputed(function() {
    return _.some( panelFlags, ko.unwrap );
  });

  self.permitType = ko.pureComputed(function() {
    return util.getIn(self, ["application", "permitType"]);
  });

  self.hideHelp = function() {
    self.showHelp(false);
  };

  self.closeSidePanel = function() {
    hub.send( "side-panel-closing");
    _.each( panelFlags, flagOff );
  };

  self.addEventListener("contextService", "leave", self.closeSidePanel);

  var pages = ["applications", "application", "attachment", "statement", "neighbors", "verdict"];

  self.disposedComputed(function() {
    // The service is not available, when the user has been logged out.
    if( self.showSidePanel ) {
      self.showSidePanel(_.includes(_.without(pages, "applications"), ko.unwrap(self.currentPage)));
      self.enableSidePanel(self.application && _.includes(pages, ko.unwrap(self.currentPage)));
    }
  });

  self.addEventListener("side-panel", "open", function( opts ) {
    _.delay( function() {
      self.showConversationPanel( true );
    }, _.get( opts, "delay", 1 ));
  });

  self.closeOnEsc = function() {
    var obj = {canClose: ko.observable( true )};
    hub.send( "side-panel-esc-pressed", obj );
    if( obj.canClose() ) {
      self.closeSidePanel();
    }
  };


};
