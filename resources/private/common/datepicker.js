/*global jQuery:false */

jQuery(function($){
  "use strict";
  if ($.datepicker) {
    $.datepicker.regional.fi = {
        closeText: 'Sulje',
        prevText: '&laquo;Edellinen',
        nextText: 'Seuraava&raquo;',
        currentText: 'T&auml;n&auml;&auml;n',
        monthNames: ['Tammikuu','Helmikuu','Maaliskuu','Huhtikuu','Toukokuu','Kes&auml;kuu','Hein&auml;kuu','Elokuu','Syyskuu','Lokakuu','Marraskuu','Joulukuu'],
        monthNamesShort: ['Tammi','Helmi','Maalis','Huhti','Touko','Kes&auml;','Hein&auml;','Elo','Syys','Loka','Marras','Joulu'],
        dayNamesShort: ['Su','Ma','Ti','Ke','To','Pe','Su'],
        dayNames: ['Sunnuntai','Maanantai','Tiistai','Keskiviikko','Torstai','Perjantai','Lauantai'],
        dayNamesMin: ['Su','Ma','Ti','Ke','To','Pe','La'],
        weekHeader: 'Vk',
        showWeek: true,
        dateFormat: 'dd.mm.yy',
        firstDay: 1,
        isRTL: false,
        buttonImage: '/img/calendar.gif',
        showMonthAfterYear: false,
        yearSuffix: ''
      };

    $.datepicker.regional.sv = {
        closeText: 'St&auml;ng',
        prevText: '&laquo;F&ouml;rra',
        nextText: 'N&auml;sta&raquo;',
        currentText: 'Idag',
        monthNames: ['Januari','Februari','Mars','April','Maj','Juni', 'Juli','Augusti','September','Oktober','November','December'],
        monthNamesShort: ['Jan','Feb','Mar','Apr','Maj','Jun', 'Jul','Aug','Sep','Okt','Nov','Dec'],
        dayNamesShort: ['S&ouml;n','M&aring;n','Tis','Ons','Tor','Fre','L&ouml;r'],
        dayNames: ['S&ouml;ndag','M&aring;ndag','Tisdag','Onsdag','Torsdag','Fredag','L&ouml;rdag'],
        dayNamesMin: ['S&ouml;','M&aring;','Ti','On','To','Fr','L&ouml;'],
        weekHeader: 'Ve',
        dateFormat: 'dd.mm.yy',
        firstDay: 1,
        isRTL: false,
        buttonImage: '/img/calendar.gif',
        showMonthAfterYear: false,
        yearSuffix: ''
      };

    $.datepicker.setDefaults($.datepicker.regional[loc.getCurrentLanguage()]);
    $.datepicker.setDefaults({
      changeMonth: true,
      changeYear: true,
      numberOfMonths: 3,
      showButtonPanel: true
    });
  }
});
