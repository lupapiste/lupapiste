LUPAPISTE.SidePanelModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.sidePanelService = util.getIn(lupapisteApp, ["services", "sidePanelService"], {});
  self.application      = self.sidePanelService.application;
  self.authorization    = self.sidePanelService.authorization;
  self.currentPage      = self.sidePanelService.currentPage;

  self.enableSidePanel = ko.observable(false);
  self.showSidePanel = ko.observable(false);
  self.sidePanelOpen = ko.observable(false);

  self.showConversationPanel = ko.observable(false);
  self.showNoticePanel = ko.observable(false);
  self.unseenNotice = self.sidePanelService.unseenNotice;
  self.showInfoPanel = ko.observable( false );
  // Info panel can exist before services (e.g., logout page).
  self.showStar = _.get( lupapisteApp, "services.infoService.showStar" );

  var panelFlags = [self.showConversationPanel,
                    self.showNoticePanel,
                    self.showInfoPanel];

  function flagOff( obs ) {
    return obs( false );
  }

  function track( event ) {
    hub.send( "track-click", {category: "side-panel",
                              label: "side-panel",
                              event: event});
  }

    self.showConversationPanel.subscribe( function() {
    if( self.showNoticePanel()) {
      track( "openConversation");
    }
  });

  self.showNoticePanel.subscribe( function() {
    if( self.showNoticePanel()) {
      hub.send( "SidePanelService::NoticeSeen");
      track( "openNotice");
    }
  });

  self.showInfoPanel.subscribe( function( flag ) {
    if( flag ) {
      hub.send( "infoService::fetch-info-links", {markSeen: true});
      track( "openInfo");
    } else {
      hub.send( "infoService::fetch-info-links", {reset: true});
    }
  });

  self.noticeIcon = self.disposedPureComputed( function() {
    return _.get( {urgent: "lupicon-warning",
                   pending: "lupicon-circle-dash"},
                  self.sidePanelService.urgency(),
                  "lupicon-document-list");
  });

  function closeOtherPanels( flag ) {
    _.each( _.without( panelFlags, flag ), flagOff );
  }

  self.showHelp = ko.observable(false);

  self.sidePanelOpen = ko.pureComputed(function() {
    return _.some( panelFlags, ko.unwrap );
  });

  self.unseenComments = ko.pureComputed(function() {
    var unseenComments = util.getIn(self, ["application", "unseenComments"]);
    return unseenComments > 99 ? "..." : unseenComments;
  });

  self.permitType = ko.pureComputed(function() {
    return util.getIn(self, ["application", "permitType"]);
  });

  self.hideHelp = function() {
    self.showHelp(false);
  };

  self.toggleConversationPanel = function() {
    self.showConversationPanel(!self.showConversationPanel());
    closeOtherPanels( self.showConversationPanel );

    if (self.showConversationPanel()) {
      self.sendEvent("SidePanelService", "UnseenCommentsSeen");
    }
  };

  self.toggleNoticePanel = function() {
    self.showNoticePanel(!self.showNoticePanel());
    closeOtherPanels( self.showNoticePanel );
  };

  self.closeSidePanel = function() {
    hub.send( "side-panel-closing");
    _.each( panelFlags, flagOff );
  };

  var pages = ["applications", "application", "attachment", "statement", "neighbors", "verdict"];

  self.disposedComputed(function() {
    self.showSidePanel(_.includes(_.without(pages, "applications"), ko.unwrap(self.currentPage)));
    self.enableSidePanel(self.application && _.includes(pages, ko.unwrap(self.currentPage)));
  });

  self.addEventListener("side-panel", "show-conversation", function() {
    if (!self.showConversationPanel()) {
      self.toggleConversationPanel();
    }
  });

  self.toggleInfoPanel = function() {
    self.showInfoPanel( !self.showInfoPanel());
    closeOtherPanels( self.showInfoPanel );
  };

  self.closeOnEsc = function() {
    var obj = {canClose: ko.observable( true )};
    hub.send( "side-panel-esc-pressed", obj );
    if( obj.canClose() ) {
      self.closeSidePanel();
    }
  };


};
