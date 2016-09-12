LUPAPISTE.InfoService = function() {
  "use strict";
  var self = this;

  self.serviceName = "infoService";
  var tmpPrefix    = ":tmp:";
  var latestAppId  = null;
  self.showStar    = ko.observable();
  self.isTemporaryId = function( id ) {
    return _.startsWith( id, tmpPrefix );
  };

  // Todo: ajax query
  self.organizationLinks = ko.pureComputed( function() {
    return _.map( ko.mapping.toJS( lupapisteApp.models.application.organizationLinks),
                  function( link ) {
                    return {url: link.url,
                            text: link.name[loc.currentLanguage],
                            isNew: _.random( 1 )};
                  });
  });

  var infoLinks = ko.observableArray();

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
  // state is not retained
  // [waiting]: Pending observable
  function fetchInfoLinks( options ) {
    options = _.defaults( options, {waiting: _.noop});
    var tmpLinks = [];
    var oldStates = options.reset
          ? []
          :_.reduce( infoLinks(),
                     function( acc, link ) {
                       if( self.isTemporaryId( link().id)) {
                         tmpLinks.push( {id: link().id});
                       }
                       return _.set( acc, link().id, {isNew: Boolean(link().isNew)});
                     }, {});

    if( !options.reset) {
      hub.send( self.serviceName + "::save-edit-state", {states: oldStates,
                                                         skipId: options.originator});
    }

    ajax.query( "info-links", {id: latestAppId})
      .pending( options.waiting )
      .success( function( res ) {
        var newLinks = _.concat( _.map( res.links,
                                        function( raw ) {
                                          return _.set( _.omit( raw, ["modified", "linkId"]),
                                                        "id", raw.linkId);
                                        }), tmpLinks );
        infoLinks( _.map( newLinks, function( link ) {
          return ko.observable( _.merge( link, oldStates[link.id]));
        }));

        if( options.reset) {
          self.showStar( _.some( infoLinks(),
                                 _.ary( _.partialRight( util.getIn, ["isNew"]),
                                        1)));
        }
        if( options.markSeen ) {
          markSeen();
        }
      })
      .call();
  }

  

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
    return lupapisteApp.models.applicationAuthModel.ok( "info-link-upsert");
  });

  function markSeen() {
      ajax.command( "mark-seen", {id: latestAppId,
                                  type: "info-links"})
        .success( function() {
          self.showStar( false );
        })
        .call();
  }

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
      // We update the view immediately, but also make info-links query later.
      self.infoLinks( links );

      ajax.command( "info-link-reorder", {id: latestAppId,
                                          linkIds: _(links)
                                          .map( function( link ) {
                                            return link().id;
                                          })
                                          .filter( _.negate( self.isTemporaryId))
                                          .value()})
        .success( function() {
          // Note: if there are links with tempIds, those are always
          // appended to the end of link list. Fortunately those
          // cannot be reordered either (no drop zone in editors).
          fetchInfoLinks( {markSeen: true});
        })
        .call();
    }
  };

  function hubscribe( event, fun ) {
    hub.subscribe( self.serviceName + "::" + event, fun );
  }

  var urlRe = /http[s]?:\/\//i;

  function makeParams( data ) {
    var url = _.trim( data.url );
    var params = {id: latestAppId,
                  text: _.trim( data.text ),
                  url: urlRe.test( url ) ? url : "http://" + url};
    return self.isTemporaryId( data.id )
      ? params
      : _.assign( params, {linkId: data.id} );
  }

  // Hub subscriptions

  hubscribe( "new", function() {
    infoLinks.push( ko.observable( {id: _.uniqueId( tmpPrefix ),
                                    text: "", url: ""
                                   }));
  });

  // Data contents [optional]:
  // id: link id
  // text: link text
  // url: link url
  // [waiting]: pending/fuse observable
  function save( data ) {
    var waiting = data.waiting || _.noop;
    var params = makeParams( data );
    ajax.command( "info-link-upsert", makeParams( data ))
      .fuse( waiting )
      .success( function( res ) {
        self.infoLink( data.id )( {id: res.linkId,
                                   text: params.text,
                                   url: params.url });
        fetchInfoLinks( {originator: res.linkId,
                         markSeen: true,
                         waiting: _.get( data, "waiting", _.noop )});
      })
      .call();
  }

  hubscribe( "save", save);

  hubscribe( "delete", function( data ) {
    infoLinks.remove( self.infoLink( data.id ));
    if( !self.isTemporaryId( data.id )) {
      ajax.command( "info-link-delete", {id: latestAppId,
                                         linkId: data.id})
        .call();
    }
  });

  hubscribe( "fetch-info-links", fetchInfoLinks );

  hubscribe( "mark-seen", markSeen);
};
