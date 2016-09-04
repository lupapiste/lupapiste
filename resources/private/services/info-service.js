LUPAPISTE.InfoService = function() {
  "use strict";
  var self = this;

  self.serviceName = "infoService";
  var tmpPrefix = ":tmp:";

  self.isTemporaryId = function( id ) {
    return _.startsWith( id, tmpPrefix );
  };

  self.organizationLinks = ko.pureComputed( function() {
    return _.map( ko.mapping.toJS( lupapisteApp.models.application.organizationLinks),
                  function( link ) {
                    return {url: link.url,
                            text: link.name[loc.currentLanguage],
                            isNew: _.random( 1 )};
                  });
  });

  // Todo: ajax query
  var infoLinks = ko.observableArray([ko.observable({id: "a", text: "First link", url: "http://example.com/first", isNew: _.random( 1 )}),
                                       ko.observable({id: "b", text: "Second link", url: "http://example.com/third", isNew: _.random( 1 )}),
                                       ko.observable({id: "c", text: "Third link", url: "http://example.com/fourth", isNew: _.random( 1 )})]);

  self.infoLinks = infoLinks;

  self.infoLink = function( linkId ) {
    return _.find( infoLinks(), function( obs ) {
      return obs().id === linkId;
    });
  };

  var appId = null;

  ko.computed( function() {
    appId = lupapisteApp.models.application.id();
    // Todo: load links
  });

  self.canEdit = ko.pureComputed( function() {
    // Todo: auth model
    return lupapisteApp.models.currentUser.isAuthority();
  });

  function hubscribe( event, fun ) {
    hub.subscribe( self.serviceName + "::" + event, fun );
  }

  function makeParams( data ) {
    var params = {id: appId,
                  text: _.trim( data.text ),
                  url: _.trim( data.url )};
    return self.isTemporaryId( data.id )
      ? params
      : _.assign( params, {linkId: data.id} );
  }

  hubscribe( "new", function() {
    infoLinks.push( ko.observable( {id: _.uniqueId( tmpPrefix ),
                                    text: "", url: ""
                                   }));
  });

  hubscribe( "save", function( data ) {
    var params = makeParams( data );
    // Todo: ajax upsert, receive real id.
    self.infoLink( data.id )( {id: _.uniqueId( "id"),
                               text: params.text,
                               url: params.url });
  });

  hubscribe( "delete", function( data ) {
    infoLinks.remove( self.infoLink( data.id ));
    // Todo: ajax delete
  });


};
