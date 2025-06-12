LUPAPISTE.SidePanelControlModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.sidePanelService;

  self.showControls = service.showSidePanel;

  function getApp() {
    return lupapisteApp.models.application;
  }

  function authed( action ) {
    return lupapisteApp.models.applicationAuthModel.ok( action );
  }

  // Conversation

  self.showConversationControl = self.disposedPureComputed( function() {
    return _.some( ["comments", "can-target-comment-to-authority",
                    "can-mark-answered"],
                   authed );
  });

  self.showConversation = service.showConversationPanel;

  self.unseenComments = self.disposedPureComputed( function() {
    return getApp().unseenComments();
  });

  // Used both for authority notice and company notes
  self.noticeIcon = self.disposedPureComputed( function() {
    return _.get( {urgent: "lupicon-warning",
                   pending: "lupicon-circle-dash"},
                  service.urgency(),
                  "lupicon-document-list");
  });

  // Authority notice

  self.showNoticeControl = self.disposedPureComputed( _.wrap( "authority-notice",
                                                              authed));
  self.showNotice = service.showNoticePanel;

  self.unseenNotice = service.unseenNotice;

  // Company notes

  self.showCompanyNotesControl = self.disposedPureComputed( _.wrap( "company-notes",
                                                                    authed));
  self.showCompanyNotes = service.showCompanyNotesPanel;

  // FIXME: LPK-5069
  self.unseenCompanyNotes = false;

  // Info

  self.showInfo = service.showInfoPanel;

  self.infoIcon = self.disposedPureComputed( function () {
    return service.showStar() ? "lupicon-circle-star" : "lupicon-circle-info";
  });

  // Toggling

  function track( event ) {
    hub.send( "track-click", {category: "side-panel",
                              label: "side-panel",
                              event: event});
  }


  function waveFlag( name, flag ) {
    switch( sprintf( "%s-%s", name, flag )) {
    case "conversation-true":
      track( "openConversation");
      hub.send("SidePanelService::UnseenCommentsSeen");
      break;
    case "notice-true":
      hub.send( "SidePanelService::NoticeSeen");
      track( "openNotice");
      break;
    case "notes-true":  // FIXME: LPK-5069
      hub.send( "SidePanelService::CompanyNotesSeen");
      track( "openCompanyNotes");
      break;
    case "info-true":
      hub.send( "infoService::fetch-links", {markSeen: true});
      track( "openInfo");
      break;
    case "info-false":
      hub.send( "infoService::fetch-links", {reset: true});
      break;
    }
  }

  function toggle( name ) {
    var flagsObs = {conversation: self.showConversation,
                    notice: self.showNotice,
                    notes: self.showCompanyNotes,
                    info: self.showInfo};

    var obs = _.get( flagsObs, name );

    obs( !obs());

    _.forEach( _.without(_.values( flagsObs ), obs ),
               function( v ) { v( false ); });


    waveFlag( name, obs() );
  }

  self.toggleFn = function( name ) {
    return _.wrap( name, toggle );
  };

};
