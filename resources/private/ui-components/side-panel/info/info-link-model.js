// Model of an info link. The link is displayed differently depending
// on the context (being edited, can be edited, view only).
// Parameters:
// id: Link id
LUPAPISTE.InfoLinkModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.infoService;

  self.link = service.infoLink( params.id );

  self.isTemporary = self.disposedPureComputed( function() {
    return service.isTemporaryId( self.link().id );
  });

  self.canEdit = self.disposedPureComputed( function() {
    return service.canEdit();
  });

  var editing = ko.observable( self.isTemporary()
                               || Boolean(self.link().editing));

  self.waiting   = ko.observable();
  self.textFocus = ko.observable( editing());
  self.urlFocus  = ko.observable();

  self.textInput = ko.observable(_.get(self.link().editing, "text"));
  self.urlInput  = ko.observable(_.get(self.link().editing, "url"));

  delete self.link().editing;

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
      self.link().editing = flag;
    }
  });

  self.editorView = self.disposedPureComputed( function() {
    return service.canEdit() && !self.editorEdit();
  });

  self.canSave = self.disposedComputed( function() {
    return !self.waiting()
      && _.trim( self.textInput() )
      && _.trim( self.urlInput());
  });

  self.save = function() {
    if( self.canSave()) {
      self.sendEvent( service.serviceName,
                      "save",
                      {id: self.link().id,
                       text: self.textInput(),
                       url: self.urlInput(),
                       waiting: self.waiting});
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

  // Typically esc closes the side panel. However, if editing is
  // ongoing, the intuitive action is cancel. Thus, side panel queries
  // via hub, if anyone objects the closing. The side panel is closed
  // if the data.canClose is ultimately true.
  self.addHubListener( "side-panel-esc-pressed",
                       function( data ) {
                         if( self.editorEdit()
                             && (self.textFocus() || self.urlFocus())) {
                           self.cancel();
                           data.canClose(false);
                         }
                       });

  self.addHubListener( "side-panel-closing", self.cancel );

  self.handleKey = function( data, event ) {
    if( event.which === 9 ) {
      // Tab switches between text fields.
      self.textFocus(!self.textFocus());
      // textFocus value has now changed.
      self.urlFocus( !self.textFocus());
    } else {
      return true;
    }
  };

  // Save-edit-state event is used by infoService to collect the edit
  // information from the link models.
  // Data contains:
  // states: object that is updated
  // skipId: if the value matches link id, no save.
  self.addEventListener( service.serviceName,
                         "save-edit-state",
                         function( data ) {
                           var id = self.link().id;
                           if( id !== data.skipId ) {
                             var obj = _.get( data.states, id, {});
                             if( self.editorEdit() ) {
                               data.states[id] = _.set( obj, "editing",
                                                        {text: self.textInput(),
                                                         url: self.urlInput()});

                             }
                           }
                         });

  // Drag'n'drop

  var ddHub = "info-link-drag-drop-event";

  self.dragging = ko.observable();

  var zoneId = null;

  self.dragStart = function( item, event ) {
    zoneId = null;
    // Dragging only allowed using the drag handle.
    var goodGrab =  _.get( document.elementFromPoint( event.clientX,
                                                      event.clientY ),
                           "className") === "lupicon-arrows-up-down";
    item.dragging( goodGrab );
    return goodGrab;
  };

  self.dragEnd = function( item ) {
    item.dragging( false );
    hub.send( ddHub, {});
  };

  self.dragDrop = function( item ) {
    service.reorder( item.link().id, zoneId );
  };

  self.showZone = ko.observable();

  self.addHubListener(ddHub, function( params ) {
    self.showZone( self.link().id === params.zoneId );
  });

  self.dragOver = function( event, dragData, zoneData ) {
    zoneId = zoneData.id;
    hub.send( ddHub, {zoneId: zoneId});
  };

  self.showDivider = self.disposedPureComputed( function() {
    return !self.showZone()
      && _.last( service.infoLinks())().id !== self.link().id;
  });

};
