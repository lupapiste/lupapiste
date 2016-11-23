// Parameters
// docModel: DocModel instance
// Path: Group path within document
LUPAPISTE.RejectNoteModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var docModel = params.docModel;
  var meta = docModel.getMeta( params.path );

  self.showNote = self.disposedPureComputed( function() {
    return !self.showEditor()
      && _.trim( self.note() )
      && (_.get( meta, "_approved.value" ) === "rejected"
          || lupapisteApp.models.currentUser.isAuthority());
  });

  self.note = ko.observable(_.get( meta, "_approved.note" ));

  // Editor (bubble dialog) related

  self.showEditor = ko.observable( false );

  self.editorNote = ko.observable();

  self.showEditor.subscribe( function( flag ) {
    if( flag ) {
      self.editorNote( self.note() );
    }
  });

  self.closeEditor = function( data, event ) {
    // Toggle editors visibility with key press
    switch( event.keyCode ) {
    case 13: // Enter
      self.note( self.editorNote());
      docModel.updateRejectNote( params.path, self.note() );
      self.showEditor( false );
      break;
    case 27: // Esc
      self.showEditor( false );
      break;
    }
    return true;
  };

  // Editor is shown after the group has been rejected
  self.addHubListener( "approval-status-" + docModel.docId,
                         function( event ) {
                           if( _.isEqual( event.path, params.path )) {
                             self.showEditor( _.get( event, "approval.value") === "rejected" );
                           }
                         });
};
