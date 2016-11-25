// Parameters [optional]. Note docModel and attachmentId are are mutually exclusive.
// docModel: DocModel instance
// attachmentId: Attachment id
// [path]: Group path within document. Requires docModel.
// [noteCss]: Note top-level CSS definitions (default reject-note class)
// [editorCss]: Note top-level CSS definitions (default reject-note-editor class)
// [storeState]: If true, then the editor state is retained after
// reinitialization. Used by attachment table (default false)
LUPAPISTE.RejectNoteModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.noteCss = params.noteCss || {"reject-note": true};
  self.editorCss = params.editorCss || {"reject-note-editor": true};
  var editObservable = _.noop;

  // The following are initialized in the context-specific init
  // functions (see below)
  self.isRejected = ko.observable();
  self.note = ko.observable();
  // Gets the note contents as argument.
  var updateNote = _.noop;

  self.showNote = self.disposedPureComputed( function() {
    return !self.showEditor()
      && _.trim( self.note() )
      && (self.isRejected()
          || lupapisteApp.models.currentUser.isAuthority());
  });

  // Editor (bubble dialog) related

  self.showEditor = ko.observable( false );

  self.editorNote = ko.observable();

  self.showEditor.subscribe( function( flag ) {
    if( flag ) {
      self.editorNote( self.note() );
    } else {
      editObservable( null );
    }
  });

  self.closeEditor = function( data, event ) {
    // Toggle editors visibility with key press
    switch( event.keyCode ) {
    case 13: // Enter
      self.note( self.editorNote());
      updateNote( self.note());
      self.showEditor( false );
      break;
    case 27: // Esc
      self.showEditor( false );
      break;
    }
    return true;
  };

  function resetRejected( rejected ) {
    self.showEditor( rejected );
    self.isRejected( rejected );
  }

  function docModelInit() {
    // Editor is shown after the group has been rejected
    // There are different events for documents (no path) and groups.

    var docModel = params.docModel;
    var path = params.path;
    var meta = docModel.getMeta( path );

    self.note( _.get( meta, "_approved.note" ) );

    self.isRejected( _.get( meta, "_approved.value" ) === "rejected" );
    updateNote = _.partial( docModel.updateRejectNote,  path || []);

    // Group
    if( path ) {
      self.addHubListener( "approval-status-" + docModel.docId,
                           function( event ) {
                             if( _.isEqual( event.path, path )) {
                               var v  = _.get( event, "approval.value");
                               if( _.includes( ["rejected", "approved"], v)) {
                                 resetRejected( v === "rejected");
                               }
                             }
                           });
    }
    else {
      // Document
      self.addHubListener( "document-approval-" + docModel.docId,
                           function( event ) {
                             if( _.isBoolean( event.approved )) {
                               resetRejected( !event.approved );
                               if( self.showEditor() ) {
                                 window.Stickyfill.rebuild();
                               }
                             }
                           });
    }
  }

  function attachmentInit() {
    var service = lupapisteApp.services.attachmentsService;
    var attachmentId = params.attachmentId;
    var attachment = service.getAttachment( attachmentId );

    self.disposedComputed( function() {
      self.isRejected( util.getIn( attachment,
                                   ["approved", "value"]) === "rejected");

      self.note( service.getRejectNote( attachmentId,
                                        util.getIn( attachment,
                                                    ["latestVersion",
                                                     "fileId"])));
    });

    updateNote = _.partial( service.rejectAttachmentNote, attachmentId );

    editObservable = params.storeState ? service.rejectAttachmentNoteEditorState : _.noop;

    if( ko.isObservable( editObservable )) {
      self.disposedComputed( function() {
        if( editObservable() === attachmentId ) {
          resetRejected( true );
        }
      });
    }

    self.addEventListener( service.serviceName,
                           "update",
                           function( event ) {
                             if( event.attachmentId === attachmentId
                                 && /^(approve|reject)-attachment$/.test( event.commandName)) {
                               var rejected = event.commandName === "reject-attachment";
                               resetRejected( rejected );
                             }
                           });
  }

  // Initialization based on parameters.
  (params.docModel ? docModelInit : attachmentInit)();

};
