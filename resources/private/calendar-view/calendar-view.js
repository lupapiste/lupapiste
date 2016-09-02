;(function() {
  "use strict";

  var components = [
    {name: "calendar-view"},
    {name: "reservation-slot-create-bubble"},
    {name: "reservation-slot-edit-bubble"},
    {name: "application-authority-calendar"},
    {name: "applicant-calendar"},
    {name: "reservation-slot-reserve-bubble"},
    {name: "reserved-slot-bubble"}
  ];

  $(_.partial(ko.registerLupapisteComponents, components));

})();
