// Convenience model for automatic assignment configuration. Sort of a proxy for the underlying organization model.
// Used from ClojureScript.
LUPAPISTE.AutomaticAssignments = function( organization ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var handlerService = lupapisteApp.services.handlerService;
  var ttService = lupapisteApp.services.triggersTargetService;
  var filters = null;
  var orgId = null;
  self.authorities = ko.observableArray();
  var refreshCallback = _.noop;

  self.handlerRoles = function() {
    return _(ko.mapping.toJS( handlerService.organizationHandlerRoles( organization )()))
      .map( function( role ) {
        if( !role.disabled ) {
          return {text: _.get( role.name, loc.currentLanguage ),
                  value: role.id};
        }})
      .filter()
      .value();
  };

  self.setRefreshCallback = function( fun ) {
    refreshCallback = fun;
  };

  self.automaticAssignmentFilters = function() {
    if( !filters) {
      // Organization may not have been fully initialized when this object was created.
      filters = ko.mapping.toJS( organization.automaticAssignmentFilters());
    }
    return filters;
  };

  self.upsertAutomaticAssignmentFilter = function( updatedFilter ) {
    var index = _.findIndex( filters, {id: updatedFilter.id});
    if( index < 0 ) {
      filters = _.concat( filters || [], [updatedFilter]);
    } else {
      filters[index] = updatedFilter;
    }
    refreshCallback();
  };

  self.deleteAutomaticAssignmentFilter = function( filterId ) {
    _.remove( filters, {id: filterId});
    refreshCallback();
  };

  self.organizationId = function() {
    return orgId;
  };

  self.operations = function() {
    return _(organization.selectedOperations())
      .map( function( ops ) {
        var group = loc( ops.permitType );
        return _.map( ops.operations, function( op ) {
          return {value: op.id,
                  text: op.text,
                  group: group};
        });
      })
      .flatten()
      .value();
  };

  self.areas = function() {
    return _.map (util.getIn( organization.features, ["features"]),
                  function( feature ) {
                    return {value: feature.id,
                            text: _.get( feature, "properties.nimi")};

                  });
  };

  self.attachmentTypes = function() {
    return _.map( ttService.data(), function( type ) {
      var id = _.get( type, "type-id");
      var group = _.get( type, "type-group");
      return {value: sprintf( "%s.%s", group, id),
              text: loc( sprintf( "attachmentType.%s.%s", group, id)),
              group: loc( sprintf( "attachmentType.%s._group_label", group))};
    });
  };

  self.noticeForms = function() {
    return _(organization.noticeForms())
      .pickBy( {enabled: true})
      .keys()
      .map( function( form ) {
        return {value: form,
                text: loc(  "notice-forms." + form )};
      })
      .sortBy( function( item ) {
        return _.indexOf( ["construction", "terrain", "location"], item.value );
      })
      .value();
  };

  function fetchAuthorities() {
    self.authorities.removeAll();
    ajax.query( "automatic-assignment-authorities", {})
      .success( function( res) {
        var items = _( res.authorities )
            .sortBy( ["lastName", "firstName"])
            .map( function( a ) {
              return _.set( {value: a.id},
                            "text",
                            _.trim( a.lastName + " " + a.firstName ));
            })
            .value();
        self.authorities( items || [] );
        refreshCallback();
      })
      .call();
  }

  self.disposedComputed( function() {
    if( organization.organizationId() !== orgId ) {
      orgId = organization.organizationId();
      filters = null;
      fetchAuthorities();
    }
  });

  // If the callback is given "directly" as the subscription listener, it does not get called?
  function notifyRefresh() {
    refreshCallback();
  }

  self.addHubListener( "organization-user-change", fetchAuthorities);
  self.addHubListener( "notice-forms-changed", notifyRefresh);
  self.addHubListener( "handlerService::role-change", notifyRefresh);

};
