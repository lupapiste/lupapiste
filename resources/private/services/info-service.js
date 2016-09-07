LUPAPISTE.InfoService = function() {
  "use strict";
  var self = this;

  self.serviceName = "infoService";
  var tmpPrefix = ":tmp:";

  self.isTemporaryId = function( id ) {
    return _.startsWith( id, tmpPrefix );
  };

  self.showStar = ko.observable();


  // Todo: ajax query
  self.organizationLinks = ko.pureComputed( function() {
    return _.map( ko.mapping.toJS( lupapisteApp.models.application.organizationLinks),
                  function( link ) {
                    return {url: link.url,
                            text: link.name[loc.currentLanguage],
                            isNew: _.random( 1 )};
                  });
  });

  // Todo: ajax query
  var infoLinks = ko.observableArray([ko.observable({id: "a", text: "First link", url: "http://example.com/first", isNew: _.random( 1 ), canEdit: _.random( 1 )}),
                                      ko.observable({id: "b", text: "Second link with a ridiculously long title that does not fit into panel. This should either be truncated or wrapped.", url: "http://example.com/third", isNew: true, canEdit: _.random( 1 )}),
                                       ko.observable({id: "c", text: "Third link", url: "http://example.com/fourth", isNew: _.random( 1 ), canEdit: _.random( 1 )})]);

  self.infoLinks = infoLinks;

  self.infoLink = function( linkId ) {
    return _.find( infoLinks(), function( obs ) {
      return obs().id === linkId;
    });
  };

  // Options:
  // [markSeen]: mark-seen command after query (default falsey)
  // [reset]: resets the links according to query result, discards
  // editing states and temporary ids. updates showStar observable
  // (default falsey)
  // [originator]: id of the event originator. The originators editing
  // state is not retained.
  // [star]: if true then showStar observable is updated (default falsey)
  function fetchInfoLinks( options ) {
    options = options || {};
    var tmpLinks = [];
    var oldStates = options.reset
          ? []
          :_.reduce( infoLinks(),
                     function( acc, link ) {
                       if( self.isTemporaryId( link().id)) {
                         tmpLinks.push( {id: link().id});
                       }
                       return _.set( acc, link().id, {isNew: link().isNew});
                     }, {});

    if( !options.reset) {
      hub.send( self.serviceName + "::save-edit-state", {states: oldStates});
    }

    // Todo: ajax query

    delete oldStates[options.originator];

    var cleanedOldies = _.filter(ko.mapping.toJS(infoLinks),
                                 _.flow( _.ary(_.partialRight( _.get, "id"), 1),
                                         _.negate(self.isTemporaryId) ));
    var newLinks = _.concat( _.map( cleanedOldies,
                                    function( link ) {
                                      return _.set( link, "isNew", false );
                                    }),
                             _.map( _.range( 2 ), function() {
                               var id = _.uniqueId( "Link-");
                               return {id: id,
                                       text: id,
                                       url: "http://example.com/" + id,
                                       isNew: true,
                                       canEdit: _.random( 1 )};
                             } ),
                           tmpLinks);
    infoLinks( _.map( newLinks, function( link ) {
      return ko.observable( _.merge( link, oldStates[link.id]));
    }));
    if( options.markSeen ) {
      // Todo: ajax query
    }
    if( options.reset) {
      self.showStar( _.some( infoLinks(),
                             _.ary( _.partialRight( util.getIn, ["isNew"]),
                                    1)));
    }
  }

  var latestAppId = "This is not a real application id.";

  ko.computed( function() {
    // Id guard to avoid unnecessary fetching (and reset), since
    // fetchInfoLinks references observables internally.
    var appId = lupapisteApp .models.application.id();
    if( appId && appId !== latestAppId ) {
      latestAppId = appId;
     fetchInfoLinks( {reset: true});
    }
  });

  self.canEdit = ko.pureComputed( function() {
    // Todo: auth model
    return lupapisteApp.models.currentUser.isAuthority();
  });

  function isTarget( targetId, link ) {
    return link().id === targetId;
  }

  self.reorder = function( moveId, afterId ) {
    if( moveId !== afterId ) {
      var links = infoLinks();
      var mover = _.find( links, _.partial( isTarget, moveId ));
      _.remove( links, _.partial( isTarget, moveId ) );
      links.splice( _.findIndex( links, _.partial( isTarget, afterId)) + 1,
                    0, mover );
      // Todo ajax query
      self.infoLinks( links );
    }
  };

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
    // Todo: ajax reorder command, followed by fetch
    infoLinks.push( ko.observable( {id: _.uniqueId( tmpPrefix ),
                                    text: "", url: ""
                                   }));
  });

  hubscribe( "save", function( data ) {
    var params = makeParams( data );
    // Todo: ajax upsert, receive real id.
    var id = self.isTemporaryId( data.id ) ? _.uniqueId( "id") : data.id;
    self.infoLink( data.id )( {id: id,
                               text: params.text,
                               url: params.url });
    fetchInfoLinks( {originator: id,
                     markSeen: true});
  });

  hubscribe( "delete", function( data ) {
    infoLinks.remove( self.infoLink( data.id ));
    // Todo: ajax delete
  });

  hubscribe( "fetch-info-links", fetchInfoLinks );
};
