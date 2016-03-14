LUPAPISTE.CompanyInviteBubbleModel = function( params ) {
  "use strict";
  var self = this;

  self.bubbleVisible = params.bubbleVisible;

  self.waiting = params.waiting;
  self.error = params.error;

  var companies = ko.observableArray();
  var entry = _.template( "<%= name %>, <%= address1 %> <%= po %>");

  // Autocomplete search term
  self.query = ko.observable("");
  self.selected = ko.observable();

  // Company list filterd by query
  self.companyOptions = ko.computed( function() {
    // Company is included in the results only if
    // every part of the query is found in the company label.
    var parts = self.query().toLowerCase().split( /[ .,]+/ );
    return _( companies())
           .filter( function( com ) {
             return _.every( parts, _.partial( _.includes, com.label.toLowerCase()));
           })
           .value();
  });

  // Item in self.companies
  function companyEntry( company ) {
    return {
      name: company.name,
      label: entry( _.defaults( company, {address1: "", po: ""})),
      id: company.id
    };
  }

  self.init = function() {
    self.query( "" );
    self.selected( null );
    self.error( false );
    ajax.query( "companies")
    .pending( self.waiting)
    .success( function( res ) {
      companies( _.map( res.companies, companyEntry ));
    })
    .error( function( res ) {
      self.error( res.text);
    } )
    .complete( function() {
      self.waiting( false );
    })
    .call();
  };

  self.confirmText = ko.computed(function() {
    return self.selected()
         ? loc( "company-invite-confirm.desc", self.selected().name)
         : null;
  });

  self.send = function() {
    hub.send( "authorized::bubble-company-invite",
              {invite: {"company-id": self.selected().id}});
  };
};
