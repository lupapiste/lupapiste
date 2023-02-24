// Neighbors table that is shown either on the statement tab (before
// verdict) or in application accordion (after verdict).
// Params (all optional):
//
//  municipalityHearsNeighborsOption: if true, the option checkbox is
//  shown (default false)
//
//  missing: l10n term that is used when there aren't any neighbors
//  (default "neighbors.missing").
LUPAPISTE.NeighborhoodModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.application = lupapisteApp.models.application;

  self.municipalityHearsNeighborsVisible = params.municipalityHearsNeighborsOption
    && self.application.municipalityHearsNeighborsVisible();

  self.missing = params.missing || "neighbors.missing";

  function latest( neighbor ) {
    return _.last( neighbor.status());
  }

  self.neighbors = self.disposedPureComputed( function() {
    return _.map( self.application.neighbors(),
                  function( m ) {
                    var late = latest( m );
                    return _.merge( m,
                                    {created: late.created(),
                                     state: late.state(),
                                     email: ko.unwrap(_.get(_.findLast( m.status(), "email"),
                                                            "email"))});
                  });
  });

  self.hasNeighbors = self.disposedPureComputed( function () {
    return _.size( self.neighbors() );
  });

  self.authOk = function( action ) {
    return lupapisteApp.models.applicationAuthModel.ok( action );
  };



  self.sendEmail = function( neighbor ) {
    hub.send( "show-dialog", {component: "send-neighbor-email",
                              minContentHeight: "10em",
                              componentParams: {name: neighbor.owner.name(),
                                                email: neighbor.email || neighbor.owner.email(),
                                                neighborId: neighbor.id()},
                              ltitle: "neighbors.sendEmail.title",
                              size: "medium"});
  };

    self.statusCompleted = function(neighbor) {
    return _.includes(["mark-done", "response-given-ok", "response-given-comments"],
                      neighbor.state );
  };

  self.markDone = function(neighbor) {
    ajax.command("neighbor-mark-done",
                 {id: self.application.id(),
                  neighborId: neighbor.id(),
                  lang: loc.getCurrentLanguage()})
      .complete(_.partial(repository.load, self.application.id(), _.noop))
      .call();
  };

  self.showStatus = function(neighbor) {
    hub.send( "show-dialog", {component: "neighbor-status",
                              componentParams: {status: ko.mapping.toJS( latest ( neighbor ))},
                              ltitle: "neighbors.status-dialog.title",
                              size: "autosized"});
    return false;
  };

};
