LUPAPISTE.InfoLinkModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.infoService;

  self.link = service.infoLink( params.id );

  self.isTemporary = self.disposedPureComputed( function() {
    return service.isTemporaryId( self.link().id );
  });

  var editing = ko.observable( self.isTemporary());

  self.textFocus = ko.observable( editing());
  self.urlFocus = ko.observable();
  self.textInput = ko.observable();
  self.urlInput = ko.observable();

  self.editorEdit = self.disposedComputed( {
    read: function() {
      return editing();
    },
    write: function( flag ) {
      if( flag ) {
        self.textInput( self.link().text );
        self.urlInput( self.link().url );
        self.textFocus( true );
        self.urlFocus( false );
      }
      editing( flag );
    }
  });

  self.editorView = self.disposedPureComputed( function() {
    return service.canEdit() && !self.editorEdit();
  });

  self.canSave = self.disposedComputed( function() {
    return _.trim( self.textInput() ) && _.trim( self.urlInput());
  });

  self.save = function() {
    if( self.canSave()) {
      self.sendEvent( service.serviceName,
                      "save",
                      {id: self.link().id,
                       text: self.textInput(),
                       url: self.urlInput()});
    }
  };

  self.remove = function() {
    self.sendEvent( service.serviceName,
                    "delete",
                    {id: self.link().id});
  };

  self.cancel = function() {
    self.editorEdit( false );
    if( self.isTemporary() ) {
      self.remove();
    }
  };

  self.handleKey = function( data, event ) {
    console.log( "handleKey")
    switch( event.which ) {
    case 9:  // Tab
      self.textFocus(!self.textFocus());
      // textFocus value has now changed.
      self.urlFocus( !self.textFocus());
      break;
    case 27:  // Esc
      self.cancel();
      break;
    default:
      return true;
    }
  };
};
