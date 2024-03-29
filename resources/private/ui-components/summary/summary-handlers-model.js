LUPAPISTE.SummaryHandlersModel = function( params ) {
  "use strict";
  var self = this;
  ko.utils.extend( self, new LUPAPISTE.SummaryBaseModel( params ));

  var service = lupapisteApp.services.handlerService;

  self.handlers = service.applicationHandlers;

  self.itemText = function( handler ) {
    var lastName  = util.getIn( handler, ["lastName"]);
    var firstName = util.getIn( handler, ["firstName"]);
    var roleName  = util.getIn( handler, ["roleName"]);
    return lastName && firstName && roleName
         ? sprintf( "%s %s (%s)", lastName, firstName, roleName )
         : "";
  };

  self.editHandlers = _.partial( hub.send,
                                 "cardService::select",
                                 {card: "edit-handlers",
                                  deck: "summary"});
};
