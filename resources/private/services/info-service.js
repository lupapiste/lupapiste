LUPAPISTE.InfoService = function() {
  "use strict";
  var self = this;

  self.serviceName = "infoService";
  var tmpPrefix    = ":tmp:";
  var latestAppId  = null;
  var markSeenNeeded = {info: false, organization: false};

  self.showStar    = ko.observable();
  self.isTemporaryId = function( id ) {
    return _.startsWith( id, tmpPrefix );
  };

  self.organizationLinks = ko.observableArray();

  function authed( command ) {
    return lupapisteApp.models.applicationAuthModel.ok( command );
  }

  function hasNewLinks( links ) {
    return _.some( links,
                   _.ary( _.partialRight( util.getIn, ["isNew"]),
                          1));
  }

  // Done explicitly, because star should not flicker when panel is
  // open.
  function updateStar() {
    self.showStar( _.some( _.values( markSeenNeeded)));
  }

  function finalizeFetch( options ) {
    options = options || {};
    if( options.reset) {
      updateStar();
    }
    if( options.markSeen ) {
      markSeen();
    }
  }

  function fetchOrganizationLinks( options ) {
    markSeenNeeded.organization = false;
    if( authed( "organization-links" )) {
      ajax.query( "organization-links", {id: latestAppId,
                                         lang: loc.currentLanguage})
        .success( function( res ) {
          self.organizationLinks( res.links );
          markSeenNeeded.organization = hasNewLinks( res.links );
          finalizeFetch( options );
        } )
        .call();
    }
  }

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
    if( authed( "info-links")) {
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

          markSeenNeeded.info = hasNewLinks( infoLinks()) || options.originator;
          finalizeFetch( options );
        })
        .call();
    }
  }
  

  ko.computed( function() {
    // Id guard to avoid unnecessary fetching (and reset), since
    // fetchInfoLinks references observables internally.
    var appId = lupapisteApp.models.application.id();
    if( appId && appId !== latestAppId ) {
      latestAppId = appId;
      markSeenNeeded = {organization: false, info: false};
      updateStar();
      self.organizationLinks([]);
      infoLinks([]);
      fetchOrganizationLinks( {reset: true});
      fetchInfoLinks( {reset: true} );
    }
  });

  self.canEdit = ko.pureComputed( function() {
    return authed( "info-link-upsert");
  });

  function markSeen() {
    self.showStar( false );
    if( markSeenNeeded.info && authed( "mark-seen")) {
      markSeenNeeded.info = false;
      ajax.command( "mark-seen", {id: latestAppId,
                                  type: "info-links"})
        .error( function() {
          // Fail silently and reset state
          markSeenNeeded.info = true;
        })
        .call();
    }
    if( markSeenNeeded.organization
        && authed( "mark-seen-organization-links")) {
      markSeenNeeded.organization = false;
      ajax.command( "mark-seen-organization-links", {id: latestAppId})
        .error( function() {
          // Fail silently and reset state
          markSeenNeeded.organization = true;
        })
        .call();
    }
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

  hubscribe( "fetch-links", function( options ) {
    fetchOrganizationLinks( options );
    fetchInfoLinks( options );
  });

  hubscribe( "mark-seen", markSeen);
};
