(function() {
  "use strict";

  function UserReport() {
    var self = this;

    self.options = [{value: "yes", text: "Kyll\u00e4"},
                    {value: "no", text: "Ei"},
                    {value: "both", text: "Sek\u00e4 ett\u00e4"}];
    self.values = [{value: ko.observable(), label: "Yritystili",
                    arg: "company"},
                   {value: ko.observable(), label: "Ammattilainen",
                    arg: "professional"},
                   {value: ko.observable(), label: "Suoramarkkinointilupa",
                    arg: "allow"}];

    
    self.link = ko.pureComputed( function() {
      return "/api/raw/user-report?"
           + _(self.values)
             .map( function( v ) {
               return sprintf( "%s=%s", v.arg, v.value())
             })
             .join( "&");
    });
  }

  $(function() {
    $("#admin-user-report").applyBindings( new UserReport());
  });

})();
